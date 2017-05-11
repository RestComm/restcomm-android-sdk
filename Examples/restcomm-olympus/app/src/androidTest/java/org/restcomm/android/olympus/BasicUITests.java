package org.restcomm.android.olympus;


import android.os.Build;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.NoMatchingViewException;
import android.support.test.espresso.ViewInteraction;
import android.support.test.espresso.matcher.BoundedMatcher;
import android.support.test.internal.util.Checks;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiSelector;
import android.support.v4.content.ContextCompat;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.TextView;

// REST  client stuff
import com.loopj.android.http.*;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;

import static android.support.test.espresso.Espresso.onData;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.AllOf.allOf;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URI;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpResponse;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.pressBack;
import static android.support.test.espresso.action.ViewActions.*;
import static android.support.test.espresso.assertion.ViewAssertions.*;
import static android.support.test.espresso.matcher.ViewMatchers.*;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class BasicUITests {
    private static final String TAG = "BasicUITests";
    final String smsText = "Hello there";

    @Rule
    public ActivityTestRule<SigninActivity> mActivityTestRule = new ActivityTestRule<>(SigninActivity.class);

    @Before
    public void signinIfNeeded()
    {
        // Signin if not already signed in
        ViewInteraction signinEditText = onView(withId(R.id.signin_username));

        try {
            signinEditText.check(matches(isDisplayed()));
        } catch (NoMatchingViewException e) {
            signinEditText = null;
        }

        // we generally try to avoid conditional logic in test cases to avoid getting overly complex, but this is an exception, at least for now
        // sign-in screen screen is in the hierarchy only when running App for the first time so we need to check and keep sign-in logic out if not showing
        if (signinEditText != null) {
            signinEditText.perform(scrollTo(), replaceText(BuildConfig.TEST_RESTCOMM_LOGIN), closeSoftKeyboard());

            ViewInteraction appCompatEditText2 = onView(withId(R.id.signin_password));
            appCompatEditText2.perform(scrollTo(), replaceText(BuildConfig.TEST_RESTCOMM_PASSWORD), closeSoftKeyboard());

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
    }

    /*
    @After
    public void afterAction() {
    }
    */


    @Test
    // Test on calling a Restcomm number, using 1235
    public void callRCNumber()
    {
        // Wait until we are REGISTERED. Notice that we could use IdlingResource to wait for specific event and avoid sleeping but I'd like to also
        // use specific timer of 5 seconds and break otherwise as it would signify a huge delay from the server side that we want to catch
        // Also, IdlingResources don't seem to allow to specify timeout for specific check() instance that would help here (for more info on that
        // check https://medium.com/azimolabs/wait-for-it-idlingresource-and-conditionwatcher-602055f32356). We could probably use IdlingPolicies
        // but still seems a bit messy.
        try {
            Thread.sleep(10000);
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

    @Test
    // Create contact +30213001 that belongs to SMS App, send text message and retrieve answer
    public void textRCNumber()
    {
        //final String smsNumber = "+1235";

        // Wait until we are REGISTERED. Notice that we could use IdlingResource to wait for specific event and avoid sleeping but I'd like to also
        // use specific timer of 5 seconds and break otherwise as it would signify a huge delay from the server side that we want to catch
        // Also, IdlingResources don't seem to allow to specify timeout for specific check() instance that would help here (for more info on that
        // check https://medium.com/azimolabs/wait-for-it-idlingresource-and-conditionwatcher-602055f32356). We could probably use IdlingPolicies
        // but still seems a bit messy.
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Add contact
        /*
        ViewInteraction floatingActionButton = onView(
                Matchers.allOf(withId(R.id.imageButton_add),
                        withParent(Matchers.allOf(withId(R.id.coordinatorLayout),
                                withParent(withId(android.R.id.content)))),
                        isDisplayed()));
        floatingActionButton.perform(click());

        ViewInteraction appCompatEditText3 = onView(
                Matchers.allOf(withId(R.id.editText_username), isDisplayed()));
        appCompatEditText3.perform(replaceText("Sms Echo"), closeSoftKeyboard());

        ViewInteraction appCompatEditText4 = onView(
                Matchers.allOf(withId(R.id.editText_sipuri), isDisplayed()));
        appCompatEditText4.perform(replaceText(smsNumber), closeSoftKeyboard());

        ViewInteraction appCompatButton2 = onView(
                Matchers.allOf(withId(android.R.id.button1), withText("Add"),
                        withParent(Matchers.allOf(withClassName(is("com.android.internal.widget.ButtonBarLayout")),
                                withParent(withClassName(is("android.widget.LinearLayout"))))),
                        isDisplayed()));
        appCompatButton2.perform(click());
        */

        ViewInteraction relativeLayout = onView(
                Matchers.allOf(childAtPosition(
                        withId(android.R.id.list),
                        1),
                        isDisplayed()));
        relativeLayout.perform(click());

        ViewInteraction appCompatEditText5 = onView(
                Matchers.allOf(withId(R.id.text_message), isDisplayed()));
        appCompatEditText5.perform(replaceText("Hello there!"), closeSoftKeyboard());

        ViewInteraction appCompatImageButton = onView(
                Matchers.allOf(withId(R.id.button_send), isDisplayed()));
        appCompatImageButton.perform(click());

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        onView(Matchers.allOf(withId(R.id.message_text),
                withText("Hello there!"), isDisplayed())).check(
                        matches(
                                withTextColor(mActivityTestRule.getActivity().getResources().getColor(R.color.colorTextSecondary))
                        )
        );

        // Verify that the echo message was received properly
        // onView(withId(R.id.label_status)).check(matches(withText("Connected")));
        //id/message_text
        /*
        onView(Matchers.allOf(withId(R.id.message_username),
                withText(smsNumber), isDisplayed())).check(matches(withText(smsNumber)));

        pressBack();

        onView(
                allOf(
                        childAtPosition(withId(android.R.id.list), 3),
                        isDisplayed()
                )
        ).perform(longClick());

        ViewInteraction appCompatTextView = onView(
                Matchers.allOf(withId(android.R.id.title), withText("Remove Contact"), isDisplayed()));
        appCompatTextView.perform(click());
        */
    }

    @Test
    public void triggerIncomingCall()
    {
        // Wait until we are REGISTERED. Notice that we could use IdlingResource to wait for specific event and avoid sleeping but I'd like to also
        // use specific timer of 5 seconds and break otherwise as it would signify a huge delay from the server side that we want to catch
        // Also, IdlingResources don't seem to allow to specify timeout for specific check() instance that would help here (for more info on that
        // check https://medium.com/azimolabs/wait-for-it-idlingresource-and-conditionwatcher-602055f32356). We could probably use IdlingPolicies
        // but still seems a bit messy.
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        /*
        // TODO: TestFairy prompt for update
        ViewInteraction appCompatButton2 = onView(
                Matchers.allOf(withId(android.R.id.button2), withText("No"),
                        withParent(Matchers.allOf(withClassName(is("com.android.internal.widget.ButtonBarLayout")),
                                withParent(withClassName(is("android.widget.LinearLayout"))))),
                        isDisplayed()));
        appCompatButton2.perform(click());
        */

        doRestRequest("call");

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ViewInteraction appCompatImageButton = onView(
                Matchers.allOf(withId(R.id.button_answer_audio),
                        withParent(Matchers.allOf(withId(R.id.layout_video_call),
                                withParent(withId(android.R.id.content)))),
                        isDisplayed()));
        appCompatImageButton.perform(click());

        // handle the permissions dialog if needed
        allowPermissionsIfNeeded();

        // Added a sleep statement to match the app's execution delay.
        // The recommended way to handle such scenarios is to use Espresso idling resources:
        // https://google.github.io/android-testing-support-library/docs/espresso/idling-resource/index.html
        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        onView(withId(R.id.label_status)).check(matches(withText("Connected")));

        ViewInteraction appCompatImageButton2 = onView(
                Matchers.allOf(withId(R.id.button_hangup),
                        withParent(Matchers.allOf(withId(R.id.layout_video_call),
                                withParent(withId(android.R.id.content)))),
                        isDisplayed()));
        appCompatImageButton2.perform(click());
    }

    @Test
    public void triggerIncomingMessage()
    {
        // Wait until we are REGISTERED. Notice that we could use IdlingResource to wait for specific event and avoid sleeping but I'd like to also
        // use specific timer of 5 seconds and break otherwise as it would signify a huge delay from the server side that we want to catch
        // Also, IdlingResources don't seem to allow to specify timeout for specific check() instance that would help here (for more info on that
        // check https://medium.com/azimolabs/wait-for-it-idlingresource-and-conditionwatcher-602055f32356). We could probably use IdlingPolicies
        // but still seems a bit messy.
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        /*
        // TODO: TestFairy prompt for update
        ViewInteraction appCompatButton2 = onView(
                Matchers.allOf(withId(android.R.id.button2), withText("No"),
                        withParent(Matchers.allOf(withClassName(is("com.android.internal.widget.ButtonBarLayout")),
                                withParent(withClassName(is("android.widget.LinearLayout"))))),
                        isDisplayed()));
        appCompatButton2.perform(click());
        */

        doRestRequest("message");

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Verify that the incoming message was received properly
        // IMPORTANT: we assume that after the message arrives there's only one message in the ListView. If more are there the assertion will fail.
        onView(Matchers.allOf(withId(R.id.message_text),
                isDisplayed())).check(matches(withText(smsText)));
    }

    //
    // Helper methods
    //
    // Custom matcher to test if color of TextView is correct
    public static Matcher<View> withTextColor(final int color) {
        Checks.checkNotNull(color);
        return new BoundedMatcher<View, TextView>(TextView.class) {
            @Override
            public boolean matchesSafely(TextView textView) {
                return color == textView.getCurrentTextColor();
            }
            @Override
            public void describeTo(Description description) {
                description.appendText("Expected Color: " + color);
            }
        };
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

    public void doRestRequest(String type)
    {
        Log.i(TAG, "------ doRestRequest()");
        AsyncHttpClient client = new AsyncHttpClient();



        // curl -X POST
        // https://ACae6e420f425248d6a26948c17a9e2acf:77f8c12cc7b8f8423e5c38b035249166@127.0.0.1:8080
        // /restcomm/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf
        // /SMS/Messages -d "To=%2B13216549878" -d "From=%2B19876543212" -d "Body=This is a test from RestComm"

        RequestParams params = new RequestParams();
        params.put("From", "alice");

        String url = "";
        if (type.equals("call")) {
            params.put("To", "client:" + BuildConfig.TEST_RESTCOMM_LOGIN);
            params.put("Url", "https://cloud.restcomm.com/restcomm/demos/hello-world.xml");
            url = "https://" + BuildConfig.TEST_RESTCOMM_ACCOUNT_SID + ":" + BuildConfig.TEST_RESTCOMM_AUTH_TOKEN +
                    "@cloud.restcomm.com/restcomm/2012-04-24/Accounts/" + BuildConfig.TEST_RESTCOMM_ACCOUNT_SID +
                    "/Calls.json";
        }
        else if (type.equals("message")) {
            // due to a bug in Restcomm (https://github.com/RestComm/Restcomm-Connect/issues/2108) To needs to have the 'client:' part removed
            params.put("To", BuildConfig.TEST_RESTCOMM_LOGIN);
            params.put("Body", smsText);
            url = "https://" + BuildConfig.TEST_RESTCOMM_ACCOUNT_SID + ":" + BuildConfig.TEST_RESTCOMM_AUTH_TOKEN +
                    "@cloud.restcomm.com/restcomm/2012-04-24/Accounts/" + BuildConfig.TEST_RESTCOMM_ACCOUNT_SID +
                    "/SMS/Messages";
        }

        client.post(url, params, new ResponseHandlerInterface() {
                    @Override
                    public void sendResponseMessage(HttpResponse httpResponse) throws IOException {

                    }

                    @Override
                    public void sendStartMessage() {

                    }

                    @Override
                    public void sendFinishMessage() {

                    }

                    @Override
                    public void sendProgressMessage(long l, long l1) {

                    }

                    @Override
                    public void sendCancelMessage() {

                    }

                    @Override
                    public void sendSuccessMessage(int i, Header[] headers, byte[] bytes) {
                        Log.i(TAG, "------ request was handled successfully");
                    }

                    @Override
                    public void sendFailureMessage(int i, Header[] headers, byte[] bytes, Throwable throwable) {

                    }

                    @Override
                    public void sendRetryMessage(int i) {

                    }

                    @Override
                    public URI getRequestURI() {
                        return null;
                    }

                    @Override
                    public void setRequestURI(URI uri) {

                    }

                    @Override
                    public Header[] getRequestHeaders() {
                        return new Header[0];
                    }

                    @Override
                    public void setRequestHeaders(Header[] headers) {

                    }

                    @Override
                    public boolean getUseSynchronousMode() {
                        return false;
                    }

                    @Override
                    public void setUseSynchronousMode(boolean b) {

                    }

                    @Override
                    public boolean getUsePoolThread() {
                        return false;
                    }

                    @Override
                    public void setUsePoolThread(boolean b) {

                    }

                    @Override
                    public void onPreProcessResponse(ResponseHandlerInterface responseHandlerInterface, HttpResponse httpResponse) {
                        Log.i(TAG, "------ response is about to be processed by the system");
                    }

                    @Override
                    public void onPostProcessResponse(ResponseHandlerInterface responseHandlerInterface, HttpResponse httpResponse) {
                        Log.i(TAG, "------ request has been fully sent, handled and finished");
                    }

                    @Override
                    public Object getTag() {
                        return null;
                    }

                    @Override
                    public void setTag(Object o) {

                    }
                });
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
