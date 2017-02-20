package com.example.luki.skru;

import android.content.Context;
import android.content.DialogInterface;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.List;

/**
 * Główna klasa programu.
 * @author Lukasz Dzwigulski
 */
public class MainActivity extends AppCompatActivity {
    public final static String EXTRA_MESSAGE = "com.example.luki.skru.MESSAGE";
    private static final String TAG = "com.example.luki.skru";

    Thread LocThread;
    ImageButton button;
    AlertDialog.Builder dialogBuilder;

    //API KEY: b59af6fd-3395-45de-837d-f640ed2d5326
    String latitude, longitude;
    OpenCellID openCellID;
    TextView lokalizacja, dane_BTS;
    TelephonyManager telephonyManager;
    GsmCellLocation cellLocation;

    String mcc ="0";  //Mobile Country Code
    String mnc = "0";  //mobile network code
    String cellid = "0"; //Cell ID
    String lac = "0";  //Location Area Code
    String OpenCellID_API = "b59af6fd-3395-45de-837d-f640ed2d5326";
    String lokKomunikat = "";

    String phoneNo = "0";

    Boolean error;
    Boolean threadchecker = false;
    Boolean threadFlaga = false;
    Boolean czySMS = false;
    Boolean SMSblad = false;
    Boolean SMSpoLAC = true;
    Boolean SMSpoCID = false;

    String GetOpenCellID_fullresult;
    int ile = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        lokalizacja = (TextView)findViewById(R.id.lokalizacja_polozenie);
        dane_BTS = (TextView)findViewById(R.id.lokalizacja_dane);

        telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        cellLocation = (GsmCellLocation)telephonyManager.getCellLocation();
        button= (ImageButton)findViewById(R.id.imageButton);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.numertelefonu:
                locDialogSMS();
                return true;
            case R.id.tryb:
                locMode();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void locDialogSMS(){
        dialogBuilder = new AlertDialog.Builder(this);
        final EditText txtinput = new EditText(this);
        if(phoneNo != "0")
            txtinput.setText(phoneNo);

        dialogBuilder.setTitle("Numer telefonu Sledzacego");
        dialogBuilder.setMessage("Podaj numer telefonu, na ktory maja przychodzic SMSy.");
        dialogBuilder.setView(txtinput);
        dialogBuilder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                 phoneNo = txtinput.getText().toString();
                 Toast.makeText(getApplicationContext(), "Numer SMS to: " + phoneNo, Toast.LENGTH_SHORT).show();
            }
        });
        dialogBuilder.setNegativeButton("Anuluj", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Toast.makeText(getApplicationContext(), "Numer SMS nie zmieniony", Toast.LENGTH_SHORT).show();
            }
        });

        //Output
        AlertDialog smsdialog = dialogBuilder.create();
        smsdialog.show();
    }

    private void locMode(){
        dialogBuilder = new AlertDialog.Builder(this);
        String[] locmode = {"Location Area Code (LAC)", "Cell Identifier (CID)"};
        int choose = 0;

        if(SMSpoLAC==false) {
            choose = 1;
        }
        else
        {
            choose = 0;
        }

        dialogBuilder.setTitle("Wysylaj SMSy przy zmianie:");
        dialogBuilder.setSingleChoiceItems(locmode, choose, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if(which == 0)
                {
                    SMSpoLAC = true;
                    if(SMSpoCID == true)
                        SMSpoCID = false;

                    Toast.makeText(getApplicationContext(), "Wybrano SMSy przy zmianie LAC", Toast.LENGTH_SHORT).show();
                }
                else if (which == 1)
                {
                    SMSpoCID = true;
                    if(SMSpoLAC == true)
                        SMSpoLAC = false;

                    Toast.makeText(getApplicationContext(), "Wybrano SMSy przy zmianie CID", Toast.LENGTH_SHORT).show();
                }
            }
        });

        AlertDialog modeDialog = dialogBuilder.create();
        modeDialog.show();
    }

    public void ButtonClick (View v)
    {
        threadchecker = !threadchecker;

        if(threadchecker) {
            button.setImageResource(R.drawable.button2);
            threadFlaga = true;

            LocThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while(threadFlaga) {
                        try {
                                getlocationnow();

                                Thread.sleep(2000);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        dane_BTS.setText("MCC: " + mcc + " MNC: " + mnc + "\n" + "Cell ID: " + cellid + " LAC: " + lac);
                                        lokalizacja.setText(lokKomunikat);
                                        //Toast.makeText(MainActivity.this, "Get Lokation", Toast.LENGTH_LONG).show();
                                    }
                                });

                                if(czySMS && phoneNo != "0")
                                {
                                    sendSMSMessage();
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            if(SMSblad) {
                                                Toast.makeText(MainActivity.this, "Blad przy wysylaniu SMS! \n Sprawdz czy podany dobry numer telefonu", Toast.LENGTH_LONG).show();
                                                Log.i(TAG, "SMS blad");
                                            }
                                            else{
                                                Toast.makeText(MainActivity.this, "SMS wyslany do: " + phoneNo, Toast.LENGTH_LONG).show();
                                                Log.i(TAG, "SMS git");
                                            }
                                        }
                                    });
                                    czySMS = false;
                                }
                                else if(czySMS && phoneNo == "0")
                                {
                                    runOnUiThread(new Runnable() {
                                                      @Override
                                                      public void run() {
                                                          Toast.makeText(MainActivity.this, "Blad przy wysylaniu SMS!" + "#" + ile + "\n Podaj numer telefonu", Toast.LENGTH_LONG).show();
                                                      }
                                                  });
                                    ile++;
                                }

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MainActivity.this, "Update wykonany!", Toast.LENGTH_SHORT).show();
                                    }
                                });
                                Thread.sleep(28000);

                                Log.i(TAG, "Update wykonany.");

                        } catch (Exception e) {
                            e.getLocalizedMessage();
                        }
                    }
                }
            });

            LocThread.start();
        }
        else{
            threadFlaga = false;
            button.setImageResource(R.drawable.buttononoff);
        }
    }

    protected void sendSMSMessage() {
        String message = "Moja obecna przyblizona lokalizacja to: \n" + lokalizacja.getText().toString();

        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNo, null, message, null, null);
            SMSblad = false;
        }

        catch (Exception e) {
            e.printStackTrace();
            SMSblad = true;
        }
    }

    public void getlocationnow(){

        telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        cellLocation = (GsmCellLocation)telephonyManager.getCellLocation();

        String networkOperator = telephonyManager.getNetworkOperator();
        String tmcc = networkOperator.substring(0, 3); //nazwa MCC
        String tmnc = networkOperator.substring(3);  //nazwa MNC

        int tcid = cellLocation.getCid(); //CELL ID LOCATION
        int tlac = cellLocation.getLac(); //NUMER LAC

        openCellID = new OpenCellID();

        if(tlac != Integer.parseInt(lac) && lac != null && lac != "0" && SMSpoLAC==true)
        {
            czySMS = true;
        }
        else if(tcid != Integer.parseInt(cellid) && cellid != null && cellid != "0" && SMSpoCID==true){
            czySMS = true;
        }

        openCellID.setMcc(tmcc);
        openCellID.setMnc(tmnc);
        openCellID.setCallID(tcid);
        openCellID.setCallLac(tlac);

        try {
            openCellID.GetOpenCellID();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            lokKomunikat = "Exception: " + e.toString();
        }
    }

    public void getLocationName()
    {
        Geocoder geocoder = new Geocoder(this);

        try {

            List<Address> addresses = geocoder.getFromLocation(Double.parseDouble(latitude),Double.parseDouble(longitude), 1);

            if(addresses != null) {

                Address fetchedAddress = addresses.get(0);
                StringBuilder strAddress = new StringBuilder();

                for(int i=0; i<fetchedAddress.getMaxAddressLineIndex(); i++) {
                    strAddress.append(fetchedAddress.getAddressLine(i)).append("\n");
                }

                lokKomunikat = "Dane: " + openCellID.getLocation() + "\n" + strAddress.toString();
            }

            else
                lokKomunikat = "Dane: " + openCellID.getLocation() + "\n" + "Nieznane dokladne polozenie urzadzenia";

        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            lokKomunikat = "Dane: " + openCellID.getLocation() + "\n" + "Nieznane dokladne polozenie urzadzenia";
        }
    }

    public class OpenCellID {

        public void setMcc(String value){
            mcc = value;
        }

        public void setMnc(String value){
            mnc = value;
        }

        public void setCallID(int value){
            cellid = String.valueOf(value);
        }

        public void setCallLac(int value){
            lac = String.valueOf(value);
        }

        public String getLocation(){
            return(latitude + " : " + longitude);
        }

        public void GetOpenCellID() throws Exception {
            new TestAsync().execute();
        }
    }

    class TestAsync extends AsyncTask<String, Integer, String>
    {
        String strURLSent;
        private Exception exception;

        public void groupURLSent(){
            strURLSent =
                    "http://www.opencellid.org/cell/get?key=" + OpenCellID_API
                            +"&mcc=" + mcc
                            +"&mnc=" + mnc
                            +"&lac=" + lac
                            +"&cellid=" + cellid
                            +"&format=json";
            /*"http://www.opencellid.org/cell/get?key=" + OpenCellID_API
                    +"&mcc=" + "260"
                    +"&mnc=" + "2"
                    +"&lac=" + "58120"
                    +"&cellid=" + "42108991"
                    +"&format=json";*/
        }

        protected String doInBackground(String... urls) {
            try {
                groupURLSent();
                HttpClient client = new DefaultHttpClient();
                HttpGet request = new HttpGet(strURLSent);
                HttpResponse response = client.execute(request);
                GetOpenCellID_fullresult = EntityUtils.toString(response.getEntity());
                return GetOpenCellID_fullresult;
            } catch (Exception e) {
                this.exception = e;
                return "error";
            }
        }

        protected void onPostExecute(String fullresult) {
            // TODO: check this.exception
            // TODO: do something with the feed
            String err = "err";

            if( fullresult.toLowerCase().indexOf(err) != -1 ){
                error = true;
                lokKomunikat = "Nie mozna pobrac danych o lokalizacji";
            }else{
                error = false;
                String[] tResult = fullresult.split(",");
                String[] latiResult = tResult[1].split(":");
                String[] longResult = tResult[0].split(":");

                latitude = latiResult[1].substring(1);
                longitude = longResult[1].substring(1);

                getLocationName();
            }

        }
    }

}
