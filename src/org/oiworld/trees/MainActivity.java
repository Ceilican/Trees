package org.oiworld.trees;

import org.oiworld.trees.R;

import org.oiworld.trees.billing.IabHelper;
import org.oiworld.trees.billing.IabResult;
import org.oiworld.trees.billing.Inventory;
import org.oiworld.trees.billing.Purchase;

import android.os.Bundle;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends Activity {
  //Debug tag, for logging
  static final String TAG = "Reflora";
  
  // Conversion rates
  static final int carbonRate = 60; // grams of CO2 per day
  static final int areaRate = 4; // square meters per tree  ToDo
  
  //The billing helper object
  IabHelper mHelper;
  
  //SKUs for products
  //static final String SKU_Tree = "tree";
  static final String SKU_Tree = "android.test.purchased";
  //static final String SKU_Tree = "android.test.canceled";
  
  // (arbitrary) request code for the purchase flow
  static final int RC_REQUEST = 10001;

  // Shared Preferences, where the number of planted trees is stored 
  SharedPreferences sharedPref; 
  
  private int getTreeCounter() {
    Log.d(TAG, "Getting tree counter.");
    int defaultValue = 0;
    return sharedPref.getInt(getString(R.string.treeCounter), defaultValue);
  }
  
  private int getReforestedArea() {
    return areaRate * getTreeCounter();
  }
  
  private int getCapturedCarbon() {
    return carbonRate * getTreeCounter();
  }
  
  private void addTrees(int quantity) {
    Log.d(TAG, "Incrementing tree counter.");
    SharedPreferences.Editor editor = sharedPref.edit();
    editor.putInt(getString(R.string.treeCounter), getTreeCounter() + quantity);
    editor.commit();
  }
  
  int treeCounter = 0;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    
    sharedPref = getPreferences(Context.MODE_PRIVATE);
    
    String Key1 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAkSbW7u/0unrluQ0gUDFPYaVg2omByuk2EuxkAnc5AWydNtOd8gJImxEb2H9EE6NIsUDZ+lkbCz2B76xF89XSDdlxrWgPRcEknINOvoj3ERtqWLPDUyDLDTYlMA6w95GijX8+//";
    String Key2 = "FoLvqkxv5JTb89NvoFfJ0HZfnI+sr9lCoz87Sh/RLhod7anvLL+TjKfsiYQrIqmHeJV+o+X25DYDDRkxhKOgtrK/Qc3Mql6M4L0FA3/r0sAuzHV9cCowPzyFm4JiDdcgrRsOmC3JqUFS6ad7eehtt0INs+IK5Y+2gHfmZfW/xFTEyyUShkP7t4k+uJTbRUoM45E4zeW/uFA1StowIDAQAB";
    String base64EncodedPublicKey = Key1 + Key2;
    
    // Create the helper, passing it our context and the public key to verify signatures with
    Log.d(TAG, "Creating IAB helper.");
    mHelper = new IabHelper(this, base64EncodedPublicKey);
   
    // Start setup. This is asynchronous and the specified listener
    // will be called once setup completes.
    Log.d(TAG, "Starting billing setup.");
    mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
        public void onIabSetupFinished(IabResult result) {
            Log.d(TAG, "Billing setup finished.");

            if (!result.isSuccess()) {
                // Oh noes, there was a problem.
                complain("Problem setting up in-app billing: " + result);
                return;
            }

            // Hooray, IAB is fully set up. Now, let's get an inventory of stuff we own.
            Log.d(TAG, "Billing setup successful. Querying inventory.");
            mHelper.queryInventoryAsync(mGotInventoryListener);
        }
    });
    
  }

  //Listener that's called when we finish querying the items we own
  IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
      public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
          Log.d(TAG, "Query inventory finished.");
          if (result.isFailure()) {
              complain("Failed to query inventory: " + result);
              return;
          }

          Log.d(TAG, "Query inventory was successful.");

          Log.d(TAG, "Inventory is: \n" + inventory.toString());
          
          // Check for tree delivery -- if we own tree, we should process it immediately
          if (inventory.hasPurchase(SKU_Tree)) {
              Log.d(TAG, "We have tree. Consuming it.");
              mHelper.consumeAsync(inventory.getPurchase(SKU_Tree), mConsumeFinishedListener);
              return;
          }

          updateUI();
          Log.d(TAG, "Initial inventory query finished; enabling main UI.");
      }
  };

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
      Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);
      if (!mHelper.handleActivityResult(requestCode, resultCode, data)) {
          super.onActivityResult(requestCode, resultCode, data);
      }
      else {
          Log.d(TAG, "onActivityResult handled by IABUtil.");
      }
  }
  
  //Callback for when a purchase is finished
  IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
      public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
          Log.d(TAG, "Purchase finished: " + result + ", purchase: " + purchase);
          if (result.isFailure()) {
              complain("Error purchasing: " + result);
              return;
          }

          Log.d(TAG, "Purchase successful.");

          if (purchase.getSku().equals(SKU_Tree)) {
              Log.d(TAG, "Purchase is tree. Starting tree consumption.");
              mHelper.consumeAsync(purchase, mConsumeFinishedListener);
          }
      }
  };
  
  
  //Called when consumption is complete
  IabHelper.OnConsumeFinishedListener mConsumeFinishedListener = new IabHelper.OnConsumeFinishedListener() {
      public void onConsumeFinished(Purchase purchase, IabResult result) {
          Log.d(TAG, "Consumption finished. Purchase: " + purchase + ", result: " + result);

          if (result.isSuccess()) {
            Log.d(TAG, "Consumption successful. Provisioning.");
            if(purchase.getSku().equals(SKU_Tree)) {
              
              addTrees(1);
              alert("You planted one more tree. You have now planted a total of " + String.valueOf(treeCounter) + " trees.");
            }
          }
          else {
              complain("Error while consuming: " + result);
          }
          updateUI();
          Log.d(TAG, "End consumption flow.");
      }
  };
  
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.activity_main, menu);
    return true;
  }
  
  /** Called when the user clicks the Plant button */
  public void plantTrees(View view) {
    Log.d(TAG, "Plant tree button clicked.");
    Log.d(TAG, "Launching purchase flow for tree.");
    mHelper.launchPurchaseFlow(this, SKU_Tree, RC_REQUEST, mPurchaseFinishedListener);
  }
  
  public void share(View view) {
    Intent sendIntent = new Intent();
    sendIntent.setAction(Intent.ACTION_SEND);
    sendIntent.putExtra(Intent.EXTRA_TEXT, "This is my text to send.");
    sendIntent.setType("text/plain");
    startActivity(Intent.createChooser(sendIntent, getResources().getText(R.string.chooserTitle)));
  }
  
  
  void complain(String message) {
    Log.e(TAG, "**** Reflora Error: " + message);
    alert("Error: " + message);
  }

  void alert(String message) {
    AlertDialog.Builder bld = new AlertDialog.Builder(this);
    bld.setMessage(message);
    bld.setNeutralButton("OK", null);
    Log.d(TAG, "Showing alert dialog: " + message);
    bld.create().show();
  }
  
  public void updateUI() {
    TextView textViewCounter = (TextView) findViewById(R.id.counterTrees);
    textViewCounter.setText(String.valueOf(getTreeCounter()));
  }
  
  
  @Override
  public void onDestroy() {
      Log.d(TAG, "Destroying helper.");
      if (mHelper != null) mHelper.dispose();
      mHelper = null;
      super.onDestroy();
  }
}
