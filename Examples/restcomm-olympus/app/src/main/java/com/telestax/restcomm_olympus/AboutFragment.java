package com.telestax.restcomm_olympus;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;

public class AboutFragment extends DialogFragment {
    // Use this instance of the interface to deliver action events
    //ContactDialogListener listener;


    /*
    public interface ContactDialogListener {
        public void onDialogPositiveClick(int type, String username, String sipuri);
        public void onDialogNegativeClick();
    }
    */

    /**
     * Create a new instance of MyDialogFragment, providing "num"
     * as an argument.
     */
    public static AboutFragment newInstance() {
        AboutFragment f = new AboutFragment();

        /*
        // Supply num input as an argument.
        Bundle args = new Bundle();
        args.putInt("type", type);
        f.setArguments(args);
        */
        return f;
    }

    // Override the Fragment.onAttach() method to instantiate the NoticeDialogListener
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        /*
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            listener = (ContactDialogListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement ContactDialogListener");
        }
        */
    }

    @Override
    public void onDetach() {
        super.onDetach();
        //listener = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    /* Not to be used when onCreateDialog is overriden (it is for non-alert dialog fragments
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_dialog_add_contact, container, false);
        txtUsername = (EditText)v.findViewById(R.id.editText_username);
        txtSipuri = (EditText)v.findViewById(R.id.editText_sipuri);

        return v;
    }
    */

    // Notice that for this doesn't work if onCreateView has been overriden as described above. To add
    // custom view when using alert we need to use builder.setView() as seen below
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Get the layout inflater
        View view = getActivity().getLayoutInflater().inflate(R.layout.fragment_about, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(view)
                .setTitle("About")
                .setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                /*
                                listener.onDialogPositiveClick(getArguments().getInt("type"), txtUsername.getText().toString(),
                                        txtSipuri.getText().toString());
                                        */
                            }
                        }
                );
                /*
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                listener.onDialogNegativeClick();
                            }
                        }
                );
                */
        return builder.create();
    }
}