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
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

public class ActionFragment extends DialogFragment {
    enum ActionType {
        ACTION_TYPE_AUDIO_CALL,
        ACTION_TYPE_VIDEO_CALL,
        ACTION_TYPE_TEXT_MESSAGE
    }

    public interface ActionListener {
        public void onActionClicked(ActionType action, String username, String sipuri);
    }

    // Use this instance of the interface to deliver action events
    ActionListener listener;

    /**
     * Create a new instance of MyDialogFragment, providing "num"
     * as an argument.
     */
    public static ActionFragment newInstance(String username, String sipuri) {
        ActionFragment f = new ActionFragment();

        // Supply num input as an argument.
        Bundle args = new Bundle();
        args.putString("username", username);
        args.putString("sipuri", sipuri);
        f.setArguments(args);

        return f;
    }

    // Override the Fragment.onAttach() method to instantiate the NoticeDialogListener
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            listener = (ActionListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement ContactDialogListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    /*
    // Notice that for this doesn't work if onCreateView has been overriden as described above. To add
    // custom view when using alert we need to use builder.setView() as seen below
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Get the layout inflater
        View view = getActivity().getLayoutInflater().inflate(R.layout.fragment_dialog_add_contact, null);
        txtUsername = (EditText)view.findViewById(R.id.editText_username);
        txtSipuri = (EditText)view.findViewById(R.id.editText_sipuri);

        String title = "Add Contact";
        String positiveText = "Add";
        if (getArguments().getInt("type") == DIALOG_TYPE_UPDATE_CONTACT) {
            title = "Update Contact";
            positiveText = "Update";

            txtUsername.setText(getArguments().getString("username", ""));
            txtSipuri.setText(getArguments().getString("sipuri", ""));
            // sipuri is not modifiable
            txtSipuri.setEnabled(false);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(view)
                .setTitle(title)
                .setPositiveButton(positiveText,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                listener.onDialogPositiveClick(getArguments().getInt("type"), txtUsername.getText().toString(),
                                        txtSipuri.getText().toString());
                            }
                        }
                )
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                listener.onDialogNegativeClick();
                            }
                        }
                );
        return builder.create();
    }
    */

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(getArguments().getString("username"))
                .setItems(R.array.actions_array, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int position) {
                        ActionType action;
                        if (position == 0) {
                            action = ActionType.ACTION_TYPE_VIDEO_CALL;
                        } else if (position == 1) {
                            action = ActionType.ACTION_TYPE_AUDIO_CALL;
                        } else {
                            action = ActionType.ACTION_TYPE_TEXT_MESSAGE;
                        }
                        listener.onActionClicked(action, getArguments().getString("username"), getArguments().getString("sipuri"));
                    }
                });
        return builder.create();
    }

}