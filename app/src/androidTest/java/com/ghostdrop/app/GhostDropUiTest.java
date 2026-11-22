package com.ghostdrop.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withHint;
import static org.hamcrest.Matchers.containsString;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class GhostDropUiTest {
    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
        new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void appLaunchesAndShowsCoreActions() {
        onView(withText("GhostDrop")).check(matches(isDisplayed()));
        onView(withText("Είμαι παραλήπτης")).check(matches(isDisplayed()));
        onView(withText("Είμαι αποστολέας")).check(matches(isDisplayed()));
        onView(withText("Αποστολή μηνύματος")).check(matches(isDisplayed()));
        onView(withText("Αποστολή αρχείου")).check(matches(isDisplayed()));
        onView(withText("Διακοπή")).check(matches(isDisplayed()));
    }

    @Test
    public void sendWithoutConnectionDoesNotCrash() {
        onView(withHint("Γράψε μήνυμα")).perform(replaceText("Δοκιμαστικό μήνυμα"), closeSoftKeyboard());
        onView(withText("Αποστολή μηνύματος")).perform(click());
        onView(withText("GhostDrop")).check(matches(isDisplayed()));
    }

    @Test
    public void stopReturnsToDisconnectedState() {
        onView(withText("Διακοπή")).perform(click());
        onView(withText(containsString("Η σύνδεση τερματίστηκε"))).check(matches(isDisplayed()));
    }
}
