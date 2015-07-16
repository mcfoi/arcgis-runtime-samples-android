package it.esri.android.facilitysurvey;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.esri.android.oauth.OAuthView;
import com.esri.core.io.UserCredentials;
import com.esri.core.map.CallbackListener;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link Oauth2Fragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link Oauth2Fragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class Oauth2Fragment extends Fragment {

    protected static final String LOG_TAG = "FacilitySurvey";

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private Context mContext;

    private OnUserCredentialsRetrieved mUserCredentialRetrievedListener;
    private OnFragmentInteractionListener mFragmentInteractionListener;


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
        public void onFragmentInteraction(Uri uri);
    }
    // Container Activity must implement this interface
    public interface OnUserCredentialsRetrieved {
        public void onUserCredentialsRetrieved(UserCredentials credentials);
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment Oauth2Fragment.
     */
    // TODO: Rename and change types and number of parameters
    public static Oauth2Fragment newInstance(String param1, String param2) {
        Oauth2Fragment fragment = new Oauth2Fragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    public Oauth2Fragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = getActivity().getApplicationContext();

        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_oauth2, container, false);
        ViewGroup layout = (ViewGroup) view.findViewById(R.id.fragment_oauth2);

        OAuthView oAuthView = createOAuth2View();

        // add OAuthview to the Fragment
        layout.addView(oAuthView,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
                );
        return view;
    }

    private OAuthView createOAuth2View() {

        // Create an instance of OAuthView
        // set client_id in string resource
        OAuthView oAuthView = new OAuthView(mContext, getResources().getString(R.string.portal_url), getResources()
                .getString(R.string.client_id), new CallbackListener<UserCredentials>() {

            @Override
            public void onError(Throwable e) {
                Log.e(LOG_TAG, "", e);
                Toast.makeText(mContext, "An error occurred retrieving credentials.\nPlease try again!", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onCallback(UserCredentials credentials) {

                // set UserCredentials
                mUserCredentialRetrievedListener.onUserCredentialsRetrieved(credentials);

                try {
                    // Save the credentials on the internal storage
                    //encryptAndSaveCredentials();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Exception while saving User Credentials.", e);
                }
                /*
                runOnUiThread(new Runnable() {
                    public void run() {
                        mImplementEncryptionDialog.show();
                    }
                });
                */
            }
        });
        return oAuthView;
    }

    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri) {
        if (mFragmentInteractionListener != null) {
            mFragmentInteractionListener.onFragmentInteraction(uri);
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mFragmentInteractionListener = (OnFragmentInteractionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnFragmentInteractionListener");
        }
        try {
            mUserCredentialRetrievedListener = (OnUserCredentialsRetrieved) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnUserCredentialsRetrieved");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mFragmentInteractionListener = null;
        mUserCredentialRetrievedListener = null;
    }

}
