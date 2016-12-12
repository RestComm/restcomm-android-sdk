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

package org.restcomm.android.olympus;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.app.Fragment;
import android.os.Handler;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.support.v7.widget.AppCompatImageButton;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;

import org.restcomm.android.sdk.RCConnection;
import org.restcomm.android.sdk.RCDevice;


public class KeypadFragment extends Fragment implements View.OnTouchListener {
   // TODO: Rename parameter arguments, choose names that match
   // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
   private static final String ARG_PARAM1 = "param1";
   private static final String ARG_PARAM2 = "param2";
   private static final String TAG = "KeypadFragment";

   private View controlView;
   private RCConnection connection;
   //private Bitmap screenshotBitmap;
   private ToneGenerator toneGenerator;

   ImageButton btnOne;
   ImageButton btnTwo;
   ImageButton btnThree;
   ImageButton btnFour;
   ImageButton btnFive;
   ImageButton btnSix;
   ImageButton btnSeven;
   ImageButton btnEight;
   ImageButton btnNine;
   ImageButton btnZero;
   ImageButton btnHash;
   ImageButton btnStar;
   ImageButton btnCancel;
   ImageView backgroundView;


   // TODO: Rename and change types of parameters
   private String mParam1;
   private String mParam2;

   private OnFragmentInteractionListener mListener;

   /**
    * Use this factory method to create a new instance of
    * this fragment using the provided parameters.
    *
    * @param param1 Parameter 1.
    * @param param2 Parameter 2.
    * @return A new instance of fragment KeypadFragment.
    */
   // TODO: Rename and change types and number of parameters
   public static KeypadFragment newInstance(String param1, String param2)
   {
      Log.i(TAG, "%% newInstance");
      KeypadFragment fragment = new KeypadFragment();
      Bundle args = new Bundle();
      args.putString(ARG_PARAM1, param1);
      args.putString(ARG_PARAM2, param2);
      fragment.setArguments(args);
      return fragment;
   }

   public KeypadFragment()
   {
      // Required empty public constructor
   }

   @Override
   public void onCreate(Bundle savedInstanceState)
   {
      Log.i(TAG, "%% onCreate");
      super.onCreate(savedInstanceState);
      if (getArguments() != null) {
         mParam1 = getArguments().getString(ARG_PARAM1);
         mParam2 = getArguments().getString(ARG_PARAM2);
      }

      toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
   }

   @Override
   public View onCreateView(LayoutInflater inflater, ViewGroup container,
                            Bundle savedInstanceState)
   {
      Log.i(TAG, "%% onCreateView");
      controlView = inflater.inflate(R.layout.fragment_keypad, container, false);

      btnOne = (ImageButton) controlView.findViewById(R.id.imageButton_1);
      btnOne.setOnTouchListener(this);
      btnTwo = (ImageButton) controlView.findViewById(R.id.imageButton_2);
      btnTwo.setOnTouchListener(this);
      btnThree = (ImageButton) controlView.findViewById(R.id.imageButton_3);
      btnThree.setOnTouchListener(this);
      btnFour = (ImageButton) controlView.findViewById(R.id.imageButton_4);
      btnFour.setOnTouchListener(this);
      btnFive = (ImageButton) controlView.findViewById(R.id.imageButton_5);
      btnFive.setOnTouchListener(this);
      btnSix = (ImageButton) controlView.findViewById(R.id.imageButton_6);
      btnSix.setOnTouchListener(this);
      btnSeven = (ImageButton) controlView.findViewById(R.id.imageButton_7);
      btnSeven.setOnTouchListener(this);
      btnEight = (ImageButton) controlView.findViewById(R.id.imageButton_8);
      btnEight.setOnTouchListener(this);
      btnNine = (ImageButton) controlView.findViewById(R.id.imageButton_9);
      btnNine.setOnTouchListener(this);
      btnZero = (ImageButton) controlView.findViewById(R.id.imageButton_0);
      btnZero.setOnTouchListener(this);
      btnStar = (ImageButton) controlView.findViewById(R.id.imageButton_star);
      btnStar.setOnTouchListener(this);
      btnHash = (ImageButton) controlView.findViewById(R.id.imageButton_hash);
      btnHash.setOnTouchListener(this);

      btnCancel = (ImageButton) controlView.findViewById(R.id.button_cancel);
      btnCancel.setOnTouchListener(this);

      backgroundView = (ImageView)controlView.findViewById(R.id.backgroundView);

      // Inflate the layout for this fragment
      return controlView;
   }

   @Override
   public void onStart()
   {
      super.onStart();
      Log.i(TAG, "%% onStart");

   }

   @Override
   public void onStop()
   {
      super.onStop();
      Log.i(TAG, "%% onStop");

   }

   @Override
   public void onResume()
   {
      super.onResume();
      Log.i(TAG, "%% onResume");

   }

   @Override
   public void onHiddenChanged(boolean hidden)
   {
      super.onHiddenChanged(hidden);
      Log.i(TAG, "%% onHiddenChanged");

      // The idea here was to create a variable alpha bitmap, to  give a nice cloudy effect where
      // other areas are more and others are less concealed from the background (i.e. call). Turned out
      // to be a big pain so I'm leaving out for now
      /*
      if (!hidden) {
         // Need to defer a bit as the view is not yet ready and the bitmap creation breaks (TODO: need to find a proper event for that)
         Handler handler = new Handler();
         // schedule a registration update after 'registrationRefresh' seconds
         Runnable runnable = new Runnable() {
            @Override
            public void run() {
               //Bitmap bitmap = getScreenShot(backgroundView);
               //Bitmap blurredBitmap = blurBitmap(bitmap);
               //backgroundView.setImageBitmap(blurredBitmap);

               final BitmapFactory.Options options = new BitmapFactory.Options();
               options.inPreferredConfig = Bitmap.Config.ARGB_8888;
               options.inScaled = false;

               // Load source grayscale bitmap
               Bitmap grayscale = BitmapFactory.decodeResource(getResources(), R.drawable.dtmf_pattern, options);
               // Place for  alpha mask. It's specifically ARGB_8888 not ALPHA_8,
               // ALPHA_8 for some reason didn't work out for me.
               Bitmap alpha = Bitmap.createBitmap(grayscale.getWidth(), grayscale.getHeight(), Bitmap.Config.ARGB_8888);
               float[] matrix = new float[] {
                     0, 0, 0, 0, 0,
                     0, 0, 0, 0, 0,
                     0, 0, 0, 0, 0,
                     1, 0, 0, 0, 0
               };
               Paint grayToAlpha = new Paint();
               grayToAlpha.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(matrix)));
               Canvas alphaCanvas = new Canvas(alpha);
               // Make sure nothing gets scaled during drawing
               alphaCanvas.setDensity(Bitmap.DENSITY_NONE);
               // Draw grayscale bitmap on to alpha canvas, using color filter that
               // takes alpha from red channel
               alphaCanvas.drawBitmap(grayscale, 0, 0, grayToAlpha);
               // Bitmap alpha now has usable alpha channel!

               backgroundView.setBackground(new BitmapDrawable(getResources(), alpha));
            }
         };
         handler.postDelayed(runnable, 0);
      }
      */
   }

   @Override
   public void onPause()
   {
      super.onPause();
      Log.i(TAG, "%% onPause");

   }

   @Override
   public void onAttach(Activity activity)
   {
      Log.i(TAG, "%% onAttach");
      super.onAttach(activity);
      try {
         mListener = (OnFragmentInteractionListener) activity;
      }
      catch (ClassCastException e) {
         throw new ClassCastException(activity.toString()
               + " must implement OnFragmentInteractionListener");
      }
   }

   @Override
   public void onDetach()
   {
      Log.i(TAG, "%% onDetach");
      super.onDetach();
      mListener = null;
   }

   public boolean onTouch(View v, MotionEvent e)
   {
      switch (e.getAction()) {
         case MotionEvent.ACTION_DOWN: {
            if (v.getId() == R.id.imageButton_1) {
               toneGenerator.startTone(ToneGenerator.TONE_DTMF_1, 300);
               connection.sendDigits("1");
            }
            else if (v.getId() == R.id.imageButton_2) {
               toneGenerator.startTone(ToneGenerator.TONE_DTMF_2, 300);
               connection.sendDigits("2");
            }
            else if (v.getId() == R.id.imageButton_3) {
               toneGenerator.startTone(ToneGenerator.TONE_DTMF_3, 300);
               connection.sendDigits("3");
            }
            else if (v.getId() == R.id.imageButton_4) {
               toneGenerator.startTone(ToneGenerator.TONE_DTMF_4, 300);
               connection.sendDigits("4");
            }
            else if (v.getId() == R.id.imageButton_5) {
               toneGenerator.startTone(ToneGenerator.TONE_DTMF_5, 300);
               connection.sendDigits("5");
            }
            else if (v.getId() == R.id.imageButton_6) {
               toneGenerator.startTone(ToneGenerator.TONE_DTMF_6, 300);
               connection.sendDigits("6");
            }
            else if (v.getId() == R.id.imageButton_7) {
               toneGenerator.startTone(ToneGenerator.TONE_DTMF_7, 300);
               connection.sendDigits("7");
            }
            else if (v.getId() == R.id.imageButton_8) {
               toneGenerator.startTone(ToneGenerator.TONE_DTMF_8, 300);
               connection.sendDigits("8");
            }
            else if (v.getId() == R.id.imageButton_9) {
               toneGenerator.startTone(ToneGenerator.TONE_DTMF_9, 300);
               connection.sendDigits("9");
            }
            else if (v.getId() == R.id.imageButton_0) {
               toneGenerator.startTone(ToneGenerator.TONE_DTMF_0, 300);
               connection.sendDigits("0");
            }
            else if (v.getId() == R.id.imageButton_star) {
               toneGenerator.startTone(ToneGenerator.TONE_DTMF_S, 300);
               connection.sendDigits("*");
            }
            else if (v.getId() == R.id.imageButton_hash) {
               toneGenerator.startTone(ToneGenerator.TONE_DTMF_P, 300);
               connection.sendDigits("#");
            }
            else if (v.getId() == R.id.button_cancel) {
               mListener.onFragmentInteraction("cancel");
            }

            // Add also visual feedback as long as the user keeps the button pressed
            v.setBackgroundResource(R.drawable.pressed_digit);
            v.invalidate();

            break;
         }
         case MotionEvent.ACTION_UP:
            // Your action here on button click
         case MotionEvent.ACTION_CANCEL: {
            final View view = v;

            // add small delay to make sure it is perceivable even for very fast touches
            new Handler().postDelayed(new Runnable() {
               @Override
               public void run()
               {
                  view.setBackgroundResource(0);
                  view.invalidate();
               }
            }, 100);
            break;
         }
      }




      return true;
   }

   /*
   btnOne.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View view, MotionEvent event)
      {
         switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
               //btnOne.getBackground().setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_ATOP);
               view.setBackgroundResource(R.drawable.pressed_digit);
               //btnOne.setBac
               view.invalidate();
               break;
            }
            case MotionEvent.ACTION_UP:
               // Your action here on button click
            case MotionEvent.ACTION_CANCEL: {
               //view.getBackground().clearColorFilter();
               view.setBackgroundResource(0);
               view.invalidate();
               break;
            }
         }
         return true;
      }
   });
   */

   /*
   @Override
   public void onClick(View v)
   {
      if (v.getId() == R.id.imageButton_1) {
         //toneGenerator.startTone(ToneGenerator.TONE_DTMF_1, 300);
         //connection.sendDigits("1");

      }
      else if (v.getId() == R.id.imageButton_2) {
         toneGenerator.startTone(ToneGenerator.TONE_DTMF_2, 300);
         connection.sendDigits("2");
      }
      else if (v.getId() == R.id.imageButton_3) {
         toneGenerator.startTone(ToneGenerator.TONE_DTMF_3, 300);
         connection.sendDigits("3");
      }
      else if (v.getId() == R.id.imageButton_4) {
         toneGenerator.startTone(ToneGenerator.TONE_DTMF_4, 300);
         connection.sendDigits("4");
      }
      else if (v.getId() == R.id.imageButton_5) {
         toneGenerator.startTone(ToneGenerator.TONE_DTMF_5, 300);
         connection.sendDigits("5");
      }
      else if (v.getId() == R.id.imageButton_6) {
         toneGenerator.startTone(ToneGenerator.TONE_DTMF_6, 300);
         connection.sendDigits("6");
      }
      else if (v.getId() == R.id.imageButton_7) {
         toneGenerator.startTone(ToneGenerator.TONE_DTMF_7, 300);
         connection.sendDigits("7");
      }
      else if (v.getId() == R.id.imageButton_8) {
         toneGenerator.startTone(ToneGenerator.TONE_DTMF_8, 300);
         connection.sendDigits("8");
      }
      else if (v.getId() == R.id.imageButton_9) {
         toneGenerator.startTone(ToneGenerator.TONE_DTMF_9, 300);
         connection.sendDigits("9");
      }
      else if (v.getId() == R.id.imageButton_0) {
         toneGenerator.startTone(ToneGenerator.TONE_DTMF_0, 300);
         connection.sendDigits("0");
      }
      else if (v.getId() == R.id.imageButton_star) {
         toneGenerator.startTone(ToneGenerator.TONE_DTMF_S, 300);
         connection.sendDigits("*");
      }
      else if (v.getId() == R.id.imageButton_hash) {
         toneGenerator.startTone(ToneGenerator.TONE_DTMF_P, 300);
         connection.sendDigits("#");
      }
      else if (v.getId() == R.id.button_cancel) {
         mListener.onFragmentInteraction("cancel");
      }
   }
   */

   /**
    * This interface must be implemented by activities that contain this
    * fragment to allow an interaction in this fragment to be communicated
    * to the activity and potentially other fragments contained in that
    * activity.
    * <p/>
    * See the Android Training lesson <a href=
    * "http://developer.android.com/training/basics/fragments/communicating.html"
    * >Communicating with Other Fragments</a> for more information.
    */
   public interface OnFragmentInteractionListener {
      // TODO: Update argument type and name
      void onFragmentInteraction(String action);
   }

   public void setConnection(RCConnection connection)
   {
      this.connection = connection;
   }

   // Bluring didn't work 100% so let's keep it commented out in case we revisit in the future.
   // Note that this needs minSdkVersion 17
   /*
   private Bitmap getScreenShot(View view) {
      //View screenView = view.getRootView();
      View screenView = view;

      screenView.setDrawingCacheEnabled(true);
      //screenView.buildDrawingCache();
      Bitmap bitmap = Bitmap.createBitmap(screenView.getDrawingCache());
      screenView.setDrawingCacheEnabled(false);
      return bitmap;
   }

   private Bitmap blurBitmap(Bitmap bitmap) {

      //Let's create an empty bitmap with the same size of the bitmap we want to blur
      Bitmap outBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);

      //Instantiate a new Renderscript
      RenderScript rs = RenderScript.create(getActivity().getApplicationContext());

      //Create an Intrinsic Blur Script using the Renderscript
      ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));

      //Create the in/out Allocations with the Renderscript and the in/out bitmaps
      Allocation allIn = Allocation.createFromBitmap(rs, bitmap);
      Allocation allOut = Allocation.createFromBitmap(rs, outBitmap);

      //Set the radius of the blur
      blurScript.setRadius(25.f);

      //Perform the Renderscript
      blurScript.setInput(allIn);
      blurScript.forEach(allOut);

      //Copy the final bitmap created by the out Allocation to the outBitmap
      allOut.copyTo(outBitmap);

      //recycle the original bitmap
      bitmap.recycle();

      //After finishing everything, we destroy the Renderscript.
      rs.destroy();

      return outBitmap;
   }
   */

}
