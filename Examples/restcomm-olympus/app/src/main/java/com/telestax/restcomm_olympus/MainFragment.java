/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 * For questions related to commercial use licensing, please contact sales@telestax.com.
 *
 */

package com.telestax.restcomm_olympus;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainFragment extends ListFragment implements ContactAdapterListener{
    private ContactsController contactsController;
    private ContactAdapter listViewAdapter;
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
        public void onAccessoryClicked(HashMap<String, String> contact);
    }

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

        listViewAdapter = new ContactAdapter(getActivity().getApplicationContext(), contactList, this);
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

        // add footer so that floating action button doesn't hide last list entry
        View footer = new View(getActivity());
        // floating action button is 56dp, let's add another 10 to give it some more space
        // convert dp to pixels
        float pixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 66, getResources().getDisplayMetrics());
        footer.setLayoutParams( new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, (int)pixels));
        // also make it unselectable
        getListView().addFooterView(footer, null, false);
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

            menu.setHeaderTitle("Edit '" + contactList.get(info.position).get("username") + "'");
            menu.add("Update Contact");
            menu.add("Remove Contact");
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        HashMap<String, String> contact = (HashMap)contactList.get(info.position);

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

    public void onAccessoryClick(int position)
    {
        HashMap<String, String> contact = (HashMap)contactList.get(position);

        mCallbacks.onAccessoryClicked(contact);
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

    public class ContactAdapter extends BaseAdapter {
        private LayoutInflater mInflater;
        private ArrayList<Map<String, String>> contactList;
        private ContactAdapterListener listener;

        public ContactAdapter(Context context, ArrayList<Map<String, String>> contactList, ContactAdapterListener listener) {
            mInflater = LayoutInflater.from(context);
            this.contactList = contactList;
            this.listener = listener;
        }

        @Override
        public int getCount() {
            return contactList.size();
        }

        @Override
        public Object getItem(int position) {
            return contactList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;
            ViewHolder holder;
            if(convertView == null) {
                view = mInflater.inflate(R.layout.contact_row_layout, parent, false);
                holder = new ViewHolder();
                holder.username = (TextView)view.findViewById(R.id.contact_username);
                holder.sipuri = (TextView)view.findViewById(R.id.contact_sipuri);
                ((ImageButton) view.findViewById(R.id.btn_accessory)).setOnClickListener(ContactButtonClickListener);
                view.setTag(holder);
            } else {
                view = convertView;
                holder = (ViewHolder)view.getTag();
            }

            Map<String, String> contact = contactList.get(position);
            holder.username.setText(contact.get("username"));
            holder.sipuri.setText(contact.get("sipuri"));

            return view;
        }

        private View.OnClickListener ContactButtonClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final int position = getListView().getPositionForView(v);
                if (position != ListView.INVALID_POSITION) {
                    listener.onAccessoryClick(position);
                }
            }
        };

        private class ViewHolder {
            public TextView username, sipuri;
            public ImageButton action;
        }
    }

}
