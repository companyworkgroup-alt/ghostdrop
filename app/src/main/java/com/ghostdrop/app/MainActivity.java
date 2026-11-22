package com.ghostdrop.app;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.widget.*;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONObject;

public class MainActivity extends Activity {
  private static final String SERVICE="com.ghostdrop.app.PROXIMITY_V1";
  private final Strategy strategy=Strategy.P2P_POINT_TO_POINT;
  private ConnectionsClient nearby;
  private String endpoint;
  private boolean connected=false, busy=false;
  private final Map<String,String> peers=new LinkedHashMap<>();
  private final Map<Long,JSONObject> metadata=new HashMap<>();
  private final Map<Long,Payload> incoming=new HashMap<>();
  private final Set<Long> completed=new HashSet<>();
  private final Map<Long,File> outgoing=new HashMap<>();
  private LinearLayout results,root;
  private TextView status,log;
  private EditText message;
  private Runnable pending;

  private final PayloadCallback payloadCallback=new PayloadCallback(){
    @Override public void onPayloadReceived(String id,Payload p){
      if(p.getType()==Payload.Type.FILE){incoming.put(p.getId(),p);saveIfReady(p.getId());return;}
      if(p.getType()!=Payload.Type.BYTES)return;
      try {
        JSONObject o=new JSONObject(new String(p.asBytes(),StandardCharsets.UTF_8));
        String type=o.optString("type");
        if(type.equals("message")){write("Παραλαβή: "+o.optString("text"));ack();}
        else if(type.equals("filemeta")){long key=o.getLong("id");metadata.put(key,o);saveIfReady(key);}
        else if(type.equals("ack")){write("Επιβεβαιώθηκε η παραλαβή");completeTransfer();}
      }catch(Exception e){error(e);}
    }
    @Override public void onPayloadTransferUpdate(String id,PayloadTransferUpdate t){
      if(t.getStatus()==PayloadTransferUpdate.Status.SUCCESS && incoming.containsKey(t.getPayloadId())){
        completed.add(t.getPayloadId());saveIfReady(t.getPayloadId());
      } else if(t.getStatus()==PayloadTransferUpdate.Status.FAILURE || t.getStatus()==PayloadTransferUpdate.Status.CANCELED){
        busy=false;write("Η μεταφορά απέτυχε ή ακυρώθηκε");
      }
    }
  };
  private final ConnectionLifecycleCallback lifecycle=new ConnectionLifecycleCallback(){
    @Override public void onConnectionInitiated(String id,ConnectionInfo info){
      if(connected || (endpoint!=null && !endpoint.equals(id))){nearby.rejectConnection(id);return;}
      endpoint=id;
      runOnUiThread(()->new AlertDialog.Builder(MainActivity.this)
        .setTitle("Σύνδεση με "+info.getEndpointName())
        .setMessage("Ελέγξτε ότι ταιριάζει ο κωδικός και στα δύο κινητά:\n\n"+info.getAuthenticationDigits())
        .setPositiveButton("Αποδοχή",(d,w)->nearby.acceptConnection(id,payloadCallback).addOnFailureListener(e->error(e)))
        .setNegativeButton("Απόρριψη",(d,w)->{nearby.rejectConnection(id);endpoint=null;})
        .setCancelable(false).show());
    }
    @Override public void onConnectionResult(String id,ConnectionResolution res){
      if(res.getStatus().isSuccess()){
        endpoint=id;connected=true;busy=false;
        nearby.stopAdvertising();nearby.stopDiscovery();
        showStatus("Συνδεδεμένο. Στείλε ένα μήνυμα ή αρχείο.");
      } else {endpoint=null;connected=false;showStatus("Η σύνδεση απέτυχε");}
    }
    @Override public void onDisconnected(String id){endpoint=null;connected=false;busy=false;showStatus("Η σύνδεση έκλεισε");}
  };
  private final EndpointDiscoveryCallback discovery=new EndpointDiscoveryCallback(){
    @Override public void onEndpointFound(String id,DiscoveredEndpointInfo info){peers.put(id,info.getEndpointName());runOnUiThread(()->drawPeers());}
    @Override public void onEndpointLost(String id){peers.remove(id);runOnUiThread(()->drawPeers());}
  };
  @Override public void onCreate(Bundle b){
    super.onCreate(b);nearby=Nearby.getConnectionsClient(this);
    ScrollView scroll=new ScrollView(this);
    root=new LinearLayout(this);root.setPadding(24,24,24,24);root.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(root);
    TextView title=new TextView(this);title.setText("GhostDrop");title.setTextSize(30);root.addView(title);
    status=new TextView(this);status.setTextSize(15);root.addView(status);
    addButton("Είμαι παραλήπτης",(v)->permissions(this::advertise));
    addButton("Είμαι αποστολέας",(v)->permissions(this::scan));
    results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);root.addView(results);
    drawPeers();
    message=new EditText(this);message.setHint("Γράψε μήνυμα");root.addView(message);
    addButton("Αποστολή μηνύματος",v->sendMessage());
    addButton("Αποστολή αρχείου",v->{
      if(!connected||busy){toast("Πρώτα σύνδεση");return;}
      Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("*/*");
      intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,501);
    });
    addButton("Διακοπή",v->{stop();showStatus("Η σύνδεση τερματίστηκε");});
    log=new TextView(this);log.setText("Καταγραφή συνεδρίας:\n");root.addView(log);
    setContentView(scroll);showStatus("Ενεργοποίησε Bluetooth και Wi-Fi");
  }
  private void addButton(String label,android.view.View.OnClickListener action){
    Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setOnClickListener(action);root.addView(b);
  }
  private void drawPeers(){
    results.removeAllViews();
    for(Map.Entry<String,String> e:peers.entrySet()){
      Button b=new Button(this);b.setText("Σύνδεση: "+e.getValue());
      b.setOnClickListener(v->connect(e.getKey()));results.addView(b);
    }
  }
  private void permissions(Runnable action){
    List<String> needed=new ArrayList<>();
    if(Build.VERSION.SDK_INT>=33)needed.add(Manifest.permission.NEARBY_WIFI_DEVICES);
    if(Build.VERSION.SDK_INT>=31){
      needed.add(Manifest.permission.BLUETOOTH_SCAN);
      needed.add(Manifest.permission.BLUETOOTH_ADVERTISE);
      needed.add(Manifest.permission.BLUETOOTH_CONNECT);
    }else needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
    List<String> missing=new ArrayList<>();
    for(String s:needed)if(checkSelfPermission(s)!=PackageManager.PERMISSION_GRANTED)missing.add(s);
    if(missing.isEmpty())action.run();
    else {pending=action;requestPermissions(missing.toArray(new String[0]),502);}
  }
  @Override public void onRequestPermissionsResult(int code,String[] names,int[] grants){
    super.onRequestPermissionsResult(code,names,grants);
    if(code!=502)return;
    boolean ok=grants.length>0;
    for(int g:grants)if(g!=PackageManager.PERMISSION_GRANTED)ok=false;
    if(ok&&pending!=null)pending.run();else showStatus("Δώσε τις άδειες κοντινών συσκευών");
    pending=null;
  }
  private void advertise(){
    stop();
    nearby.startAdvertising("Android-"+Build.MODEL,SERVICE,lifecycle,
      new AdvertisingOptions.Builder().setStrategy(strategy).build())
      .addOnSuccessListener(v->showStatus("Η συσκευή είναι ορατή"))
      .addOnFailureListener(this::error);
  }
  private void scan(){
    stop();peers.clear();drawPeers();
    nearby.startDiscovery(SERVICE,discovery,new DiscoveryOptions.Builder().setStrategy(strategy).build())
      .addOnSuccessListener(v->showStatus("Αναζήτηση κοντινών κινητών…"))
      .addOnFailureListener(this::error);
  }
  private void connect(String id){
    if(connected||endpoint!=null){toast("Υπάρχει ενεργή σύνδεση");return;}
    nearby.requestConnection("Android-"+Build.MODEL,id,lifecycle)
      .addOnSuccessListener(v->showStatus("Αναμονή έγκρισης"))
      .addOnFailureListener(this::error);
  }
  private void sendMessage(){
    if(!connected||busy){toast("Συνδέσου πρώτα");return;}
    String value=message.getText().toString().trim();if(value.isEmpty())return;
    try {
      busy=true;
      byte[] bytes=new JSONObject().put("type","message").put("text",value).toString().getBytes(StandardCharsets.UTF_8);
      nearby.sendPayload(endpoint,Payload.fromBytes(bytes))
        .addOnSuccessListener(v->{write("Αποστολή: "+value);runOnUiThread(()->message.setText(""));})
        .addOnFailureListener(e->{busy=false;error(e);});
    }catch(Exception e){busy=false;error(e);}
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(request!=501||result!=RESULT_OK||data==null||data.getData()==null||!connected||busy)return;
    Uri uri=data.getData();busy=true;
    new Thread(()->{
      try{
        String name=fileName(uri);
        File dir=new File(getCacheDir(),"out");dir.mkdirs();
        File temp=File.createTempFile("send_",".tmp",dir);
        try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){
          if(in==null)throw new IOException("Unable to open file");
          byte[] buffer=new byte[65536];int n;
          while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
        }
        Payload file=Payload.fromFile(temp);outgoing.put(file.getId(),temp);
        JSONObject meta=new JSONObject().put("type","filemeta").put("id",file.getId()).put("name",name);
        nearby.sendPayload(endpoint,Payload.fromBytes(meta.toString().getBytes(StandardCharsets.UTF_8)))
          .addOnSuccessListener(v->nearby.sendPayload(endpoint,file)
            .addOnSuccessListener(w->write("Στάλθηκε: "+name))
            .addOnFailureListener(e->{busy=false;error(e);}))
          .addOnFailureListener(e->{busy=false;error(e);});
      }catch(Exception e){busy=false;error(e);}
    }).start();
  }
  private String fileName(Uri uri){
    try(Cursor c=getContentResolver().query(uri,null,null,null,null)){
      if(c!=null&&c.moveToFirst()){
        int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if(i>=0)return c.getString(i);
      }
    }catch(Exception ignored){}
    return "received-file";
  }
  private void saveIfReady(long id){
    Payload p=incoming.get(id);JSONObject meta=metadata.get(id);
    if(p==null||meta==null||!completed.contains(id))return;
    incoming.remove(id);metadata.remove(id);completed.remove(id);
    new Thread(()->{
      try{
        File dir=new File(getExternalFilesDir(null),"Received");
        if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create folder");
        String name=meta.optString("name","file").replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_");
        File dest=new File(dir,UUID.randomUUID().toString().substring(0,8)+"_"+name);
        try(InputStream in=new FileInputStream(p.asFile().asJavaFile());OutputStream out=new FileOutputStream(dest)){
          byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
        }
        write("Αποθηκεύτηκε: "+dest.getAbsolutePath());ack();
      }catch(Exception e){error(e);}
    }).start();
  }
  private void ack(){
    try{
      if(!connected||endpoint==null)return;
      byte[] data=new JSONObject().put("type","ack").toString().getBytes(StandardCharsets.UTF_8);
      nearby.sendPayload(endpoint,Payload.fromBytes(data))
        .addOnSuccessListener(v->completeTransfer()).addOnFailureListener(this::error);
    }catch(Exception e){error(e);}
  }
  private void completeTransfer(){
    new Handler(Looper.getMainLooper()).postDelayed(()->{
      stop();showStatus("Η μεταφορά ολοκληρώθηκε, η σύνδεση έκλεισε");
    },1200);
  }
  private void stop(){
    nearby.stopAdvertising();nearby.stopDiscovery();nearby.stopAllEndpoints();
    connected=false;busy=false;endpoint=null;peers.clear();metadata.clear();incoming.clear();completed.clear();
    for(File f:outgoing.values())f.delete();outgoing.clear();
    runOnUiThread(this::drawPeers);
  }
  private void showStatus(String s){runOnUiThread(()->{if(status!=null)status.setText("Κατάσταση: "+s);});}
  private void write(String s){runOnUiThread(()->{if(log!=null)log.append("\n"+s);});}
  private void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_SHORT).show());}
  private void error(Exception e){write("Σφάλμα: "+e.getMessage());showStatus("Σφάλμα: "+e.getMessage());}
  @Override protected void onDestroy(){stop();super.onDestroy();}
}
