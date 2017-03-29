package org.restcomm.android.olympus;


import android.os.Build;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.NoMatchingViewException;
import android.support.test.espresso.ViewInteraction;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiSelector;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

//import android.support.test.

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import static org.hamcrest.core.AllOf.allOf;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.pressBack;
import static android.support.test.espresso.action.ViewActions.*;
import static android.support.test.espresso.assertion.ViewAssertions.*;
import static android.support.test.espresso.matcher.ViewMatchers.*;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class SigningAndCall1235 {
    private static final String TAG = "SigningAndCall1235";

    @Rule
    public ActivityTestRule<SigninActivity> mActivityTestRule = new ActivityTestRule<>(SigninActivity.class);

    @Test
    public void signingAndCall1235() {
        ViewInteraction signinEditText = onView(withId(R.id.signin_username));

        try {
            signinEditText.check(matches(isDisplayed()));
        } catch (NoMatchingViewException e) {
            signinEditText = null;
        }

        // we generally try to avoid conditional logic in test cases to avoid getting overly complex, but this is an exception, at least for now
        // sign-in screen screen is in the hierarchy only when running App for the first time so we need to check and keep sign-in logic out if not showing
        if (signinEditText != null) {
            signinEditText.perform(scrollTo(), replaceText("bob"), closeSoftKeyboard());

            ViewInteraction appCompatEditText2 = onView(withId(R.id.signin_password));
            appCompatEditText2.perform(scrollTo(), replaceText("invalid"), closeSoftKeyboard());

            onView(withId(R.id.signin_domain)).perform(scrollTo(), replaceText("cloud.restcomm.com"), closeSoftKeyboard());

            onView(
                    allOf(
                            withId(R.id.signin_button), withText("Sign in"),
                            withParent(
                                    allOf(
                                            withId(R.id.email_login_form),
                                            withParent(withId(R.id.login_form))
                                    )
                            )
                    )
            ).perform(scrollTo(), click());
        }

        // Wait until we are REGISTERED. Notice that we could use IdlingResource to wait for specific event and avoid sleeping but I'd like to also
        // use specific timer of 5 seconds and break otherwise as it would signify a huge delay from the server side that we want to catch
        // Also, IdlingResources don't seem to allow to specify timeout for specific check() instance that would help here (for more info on that
        // check https://medium.com/azimolabs/wait-for-it-idlingresource-and-conditionwatcher-602055f32356). We could probably use IdlingPolicies
        // but still seems a bit messy.
        try {
            Thread.sleep(7000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        onView(
                allOf(
                        childAtPosition(withId(android.R.id.list), 1),
                        isDisplayed()
                )
        ).perform(click());

        onView(
                allOf(
                        withId(R.id.action_audio_call),
                        withContentDescription("Settings"),
                        isDisplayed()
                )
        ).perform(click());

        // handle the permissions dialog if needed
        allowPermissionsIfNeeded();

        // If after 10 seconds we 're still not connected then we have an issue.
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        onView(withId(R.id.label_status)).check(matches(withText("Connected")));

        onView(
                allOf(
                        withId(R.id.button_hangup),
                        withParent(allOf(withId(R.id.layout_video_call), withParent(withId(android.R.id.content)))),
                        isDisplayed()
                )
        ).perform(click());

        /* Seems that we 're ok without that still. Lets keep just in case we need it in the future
        // Added a sleep statement to match the app's execution delay.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        */

        pressBack();
    }

    private static Matcher<View> childAtPosition(
            final Matcher<View> parentMatcher, final int position) {

        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("Child at position " + position + " in parent ");
                parentMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(View view) {
                ViewParent parent = view.getParent();
                return parent instanceof ViewGroup && parentMatcher.matches(parent)
                        && view.equals(((ViewGroup) parent).getChildAt(position));
            }
        };
    }

    private void allowPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 23) {
            // Initialize UiDevice instance
            UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

            // notice that we typically receive 2-3 permission prompts, hence the loop
            while (true) {
                UiObject allowPermissions = uiDevice.findObject(new UiSelector().text("Allow"));
                if (allowPermissions.exists()) {
                    try {
                        allowPermissions.click();
                    } catch (UiObjectNotFoundException e) {
                        e.printStackTrace();
                        Log.e(TAG, "There is no permissions dialog to interact with ");
                    }
                } else {
                    break;
                }
            }
        }
    }
}
