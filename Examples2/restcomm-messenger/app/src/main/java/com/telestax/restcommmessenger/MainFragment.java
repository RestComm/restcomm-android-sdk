package com.telestax.restcommmessenger;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import org.mobicents.restcomm.android.client.sdk.RCDevice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

//import com.example.anomatos.myapplication.dummy.DummyContent;

/**
 * A list fragment representing a list of Items. This fragment
 * also supports tablet devices by allowing list items to be given an
 * 'activated' state upon selection. This helps indicate which item is
 * currently being viewed in a ItemDetailFragment}.
 * <p/>
 * Activities containing this fragment MUST implement the {@link Callbacks}
 * interface.
 */
public class MainFragment extends ListFragment {
    private ContactsController contactsController;
    private SimpleAdapter listViewAdapter;
    private ArrayList<Map<String, String>> contactList;

    enum ContactSelectionType {
        VIDEO_CALL,
        AUDIO_CALL,
        TEXT_MESSAGE,
    }

    /**
     * The serialization (saved instance state) Bundle key representing the
     * activated item position. Only used on tablets.
     */
    private static final String STATE_ACTIVATED_POSITION = "activated_position";

    /**
     * The fragment's current callback object, which is notified of list item
     * clicks.
     */
    private Callbacks mCallbacks = null;

    /**
     * The current activated item position. Only used on tablets.
     */
    private int mActivatedPosition = ListView.INVALID_POSITION;

    /**
     * A callback interface that all activities containing this fragment must
     * implement. This mechanism allows activities to be notified of item
     * selections.
     */
    public interface Callbacks {
        /**
         * Callback for when an item has been selected.
         */
        public void onItemSelected(HashMap<String, String> contact, ContactSelectionType type);
        public void onContactUpdate(HashMap<String, String> contact, int type);
    }

    /**
     * A dummy implementation of the {@link Callbacks} interface that does
     * nothing. Used only when this fragment is not attached to an activity.
     */
    /*
    private static Callbacks sDummyCallbacks = new Callbacks() {
        @Override
        public void onItemSelected(String id) {
        }
    };
    */

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public MainFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        contactsController = new ContactsController(getActivity().getApplicationContext());
        contactList = contactsController.initializeContacts();
        String[] from = { "username", "sipuri" };
        int[] to = { R.id.contact_username, R.id.contact_sipuri };

        listViewAdapter = new SimpleAdapter(getActivity().getApplicationContext(), contactList,
                R.layout.contact_row_layout, from, to);
        setListAdapter(listViewAdapter);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Restore the previously serialized activated item position.
        if (savedInstanceState != null
                && savedInstanceState.containsKey(STATE_ACTIVATED_POSITION)) {
            setActivatedPosition(savedInstanceState.getInt(STATE_ACTIVATED_POSITION));
        }

        registerForContextMenu(getListView());
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // Activities containing this fragment must implement its callbacks.
        if (!(activity instanceof Callbacks)) {
            throw new IllegalStateException("Activity must implement fragment's callbacks.");
        }

        mCallbacks = (Callbacks) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();

        // Reset the active callbacks interface to the dummy implementation.
        mCallbacks = null;
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        super.onListItemClick(listView, view, position, id);

        HashMap item = (HashMap)getListView().getItemAtPosition(position);
        // Notify the active callbacks interface (the activity, if the
        // fragment is attached to one) that an item has been selected.
        mCallbacks.onItemSelected(item, ContactSelectionType.VIDEO_CALL);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mActivatedPosition != ListView.INVALID_POSITION) {
            // Serialize and persist the activated item position.
            outState.putInt(STATE_ACTIVATED_POSITION, mActivatedPosition);
        }
    }

    /**
     * Turns on activate-on-click mode. When this mode is on, list items will be
     * given the 'activated' state when touched.
     */
    public void setActivateOnItemClick(boolean activateOnItemClick) {
        // When setting CHOICE_MODE_SINGLE, ListView will automatically
        // give items the 'activated' state when touched.
        getListView().setChoiceMode(activateOnItemClick
                ? ListView.CHOICE_MODE_SINGLE
                : ListView.CHOICE_MODE_NONE);
    }

    private void setActivatedPosition(int position) {
        if (position == ListView.INVALID_POSITION) {
            getListView().setItemChecked(mActivatedPosition, false);
        } else {
            getListView().setItemChecked(position, true);
        }

        mActivatedPosition = position;
    }

    // Context Menu stuff
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (v.getId() == android.R.id.list) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;

            menu.setHeaderTitle(contactList.get(info.position).get("username"));
            menu.add("Video Call");
            menu.add("Audio Call");
            menu.add("Send Text Message");
            menu.add("Update Contact");
            menu.add("Remove Contact");
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        HashMap<String, String> contact = (HashMap)contactList.get(info.position);

        if (item.getTitle().toString().equals("Video Call")) {
            mCallbacks.onItemSelected(contact, ContactSelectionType.VIDEO_CALL);
        }
        if (item.getTitle().toString().equals("Audio Call")) {
            mCallbacks.onItemSelected(contact, ContactSelectionType.AUDIO_CALL);
        }
        if (item.getTitle().toString().equals("Send Text Message")) {
            mCallbacks.onItemSelected(contact, ContactSelectionType.TEXT_MESSAGE);
        }
        if (item.getTitle().toString().equals("Update Contact")) {
            mCallbacks.onContactUpdate(contact, AddUserDialogFragment.DIALOG_TYPE_UPDATE_CONTACT);
        }
        if (item.getTitle().toString().equals("Remove Contact")) {
            contactsController.removeContact(contactList, contact.get("username"), contact.get("sipuri"));
            this.listViewAdapter.notifyDataSetChanged();
        }

        return true;
    }

    // Called by Activity when contact is to be updated
    public void updateContact(int type, String username, String sipuri)
    {
        if (type == AddUserDialogFragment.DIALOG_TYPE_ADD_CONTACT) {
            if (username.isEmpty() || sipuri.isEmpty()) {
                showOkAlert("Addition Cancelled", "Both Username and SIP URI fields must be provided");
                return;
            }
            else {
                this.contactsController.addContact(contactList, username, sipuri);
            }
        }
        else {
            this.contactsController.updateContact(contactList, username, sipuri);
        }
        // notify adapter that ListView needs to be updated
        this.listViewAdapter.notifyDataSetChanged();
    }

    // Helper methods
    private void showOkAlert(final String title, final String detail) {
        AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).create();
        alertDialog.setTitle(title);
        alertDialog.setMessage(detail);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        alertDialog.show();
    }

}
