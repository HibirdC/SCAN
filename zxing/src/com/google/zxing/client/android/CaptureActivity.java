/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.client.android;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.ResultPoint;
import com.google.zxing.client.android.camera.CameraManager;
import com.google.zxing.client.android.clipboard.ClipboardInterface;
import com.google.zxing.client.android.result.CodeData;
import com.google.zxing.client.android.result.DBManager;
import com.google.zxing.client.android.result.ResultButtonListener;
import com.google.zxing.client.android.result.ResultHandler;
import com.google.zxing.client.android.result.ResultHandlerFactory;
import com.zhiyong.code.R;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This activity opens the camera and does the actual scanning on a background thread. It draws a
 * viewfinder to help the user place the barcode correctly, shows feedback as the image processing
 * is happening, and then overlays the results when a scan is successful.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */
public final class CaptureActivity extends Activity implements SurfaceHolder.Callback {

  private static final String TAG = CaptureActivity.class.getSimpleName();

  private static final long DEFAULT_INTENT_RESULT_DURATION_MS = 1500L;
  private static final long BULK_MODE_SCAN_DELAY_MS = 1000L;

  private static final String[] ZXING_URLS = { "http://zxing.appspot.com/scan", "zxing://scan/" };

  public static final int HISTORY_REQUEST_CODE = 0x0000bacc;

  private static final Collection<ResultMetadataType> DISPLAYABLE_METADATA_TYPES =
      EnumSet.of(ResultMetadataType.ISSUE_NUMBER,
                 ResultMetadataType.SUGGESTED_PRICE,
                 ResultMetadataType.ERROR_CORRECTION_LEVEL,
                 ResultMetadataType.POSSIBLE_COUNTRY);

  private CameraManager cameraManager;
  private CaptureActivityHandler handler;
  private Result savedResultToShow;
  private ViewfinderView viewfinderView;
  private TextView statusView;
  private View resultView;
  private Result lastResult;
  private boolean hasSurface;
  private boolean copyToClipboard;
  private IntentSource source;
  private String sourceUrl;
  private ScanFromWebPageManager scanFromWebPageManager;
  private Collection<BarcodeFormat> decodeFormats;
  private Map<DecodeHintType,?> decodeHints;
  private String characterSet;
  private InactivityTimer inactivityTimer;
  private BeepManager beepManager;
  private AmbientLightManager ambientLightManager;
  private String UserName ="Admin";
  private String OperateType = "员工";
  private String LastScanTime = "0000-00-00 00:00:00";
  private String LastCode = "";
  private boolean IsComplete ;
  private ProgressDialog pd;
  private String Host = "192.168.0.44";
  private int Port = 1987;
  private int PacketSize = 5;
  private boolean SendOver = false;
  ViewfinderView getViewfinderView() {
    return viewfinderView;
  }

  public Handler getHandler() {
    return handler;
  }

  CameraManager getCameraManager() {
    return cameraManager;
  }

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);

    Window window = getWindow();
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.capture);

    hasSurface = false;
    inactivityTimer = new InactivityTimer(this);
    beepManager = new BeepManager(this);
    ambientLightManager = new AmbientLightManager(this);
    UserName = GetUserInfo();
    if(UserName.isEmpty()||UserName.equals(""))
    {
    	//输入一个用户名
    	showWaiterAuthorizationDialog();
    }
    PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
  }
	// 显示对话框
	public void showWaiterAuthorizationDialog() {

		// LayoutInflater是用来找layout文件夹下的xml布局文件，并且实例化
		LayoutInflater factory = LayoutInflater.from(CaptureActivity.this);
		// 把activity_login中的控件定义在View中
		final View textEntryView = factory.inflate(R.layout.login,
				null);

		// 将LoginActivity中的控件显示在对话框中
		new AlertDialog.Builder(CaptureActivity.this)
		// 对话框的标题
				.setTitle("登陆")
				// 设定显示的View
				.setView(textEntryView)
				// 对话框中的“登陆”按钮的点击事件
				.setPositiveButton("登陆", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {

						// 获取用户输入的“用户名”，“密码”
						// 注意：textEntryView.findViewById很重要，因为上面factory.inflate(R.layout.activity_login,
						// null)将页面布局赋值给了textEntryView了
						final EditText etUserName = (EditText) textEntryView
								.findViewById(R.id.etuserName);
						final EditText etPassword = (EditText) textEntryView
								.findViewById(R.id.etPWD);
						// 将页面输入框中获得的“用户名”，“密码”转为字符串
						String Name = etUserName.getText().toString()
								.trim();
						String password = etPassword.getText().toString()
								.trim();
						if (!Name.equals("") && password.equals("88888888")) {
							UserName = Name;
							InsertUserInfo(Name,password);
						}  
						if(Name.isEmpty()||Name.equals("")||!password.equals("88888888"))
						{
			          		Toast.makeText(getBaseContext(), "用户名或者密码错误", Toast.LENGTH_LONG).show();
	                        try {  
	                            // 注意此处是通过反射，修改源代码类中的字段mShowing为true，系统会认为对话框打开  
	                            // 从而调用dismiss()  
	                            Field field = dialog.getClass().getSuperclass()  
	                                    .getDeclaredField("mShowing");  
	                            field.setAccessible(true);  
	                            field.set(dialog, false);  
	                            dialog.dismiss();  

	                        } catch (Exception e) {  

	                        } 
			          		return;
						}
					}
				})
				// 对话框的“退出”单击事件
				.setNegativeButton("退出", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						CaptureActivity.this.finish();
					}
				})
				// 设置dialog是否为模态，false表示模态，true表示非模态
				.setCancelable(false)
				// 对话框的创建、显示
				.create().show();
	}
	private void InsertUserInfo(String Name,String Pwd)
	{
		DBManager db = new DBManager(this);
		db.InsertUserInfo(Name, Pwd);
		db.closeDB();
	}
  @Override
  protected void onResume() {
    super.onResume();
    
    // CameraManager must be initialized here, not in onCreate(). This is necessary because we don't
    // want to open the camera driver and measure the screen size if we're going to show the help on
    // first launch. That led to bugs where the scanning rectangle was the wrong size and partially
    // off screen.
    cameraManager = new CameraManager(getApplication());

    viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
    viewfinderView.setCameraManager(cameraManager);

    resultView = findViewById(R.id.result_view);
    statusView = (TextView) findViewById(R.id.status_view);

    handler = null;
    lastResult = null;

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

    if (prefs.getBoolean(PreferencesActivity.KEY_DISABLE_AUTO_ORIENTATION, true)) {
      setRequestedOrientation(getCurrentOrientation());
    } else {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    resetStatusView();


    beepManager.updatePrefs();
    ambientLightManager.start(cameraManager);

    inactivityTimer.onResume();

    Intent intent = getIntent();

    copyToClipboard = prefs.getBoolean(PreferencesActivity.KEY_COPY_TO_CLIPBOARD, true)
        && (intent == null || intent.getBooleanExtra(Intents.Scan.SAVE_HISTORY, true));

    source = IntentSource.NONE;
    sourceUrl = null;
    scanFromWebPageManager = null;
    decodeFormats = null;
    characterSet = null;

    if (intent != null) {

      String action = intent.getAction();
      String dataString = intent.getDataString();

      if (Intents.Scan.ACTION.equals(action)) {

        // Scan the formats the intent requested, and return the result to the calling activity.
        source = IntentSource.NATIVE_APP_INTENT;
        decodeFormats = DecodeFormatManager.parseDecodeFormats(intent);
        decodeHints = DecodeHintManager.parseDecodeHints(intent);

        if (intent.hasExtra(Intents.Scan.WIDTH) && intent.hasExtra(Intents.Scan.HEIGHT)) {
          int width = intent.getIntExtra(Intents.Scan.WIDTH, 0);
          int height = intent.getIntExtra(Intents.Scan.HEIGHT, 0);
          if (width > 0 && height > 0) {
            cameraManager.setManualFramingRect(width, height);
          }
        }

        if (intent.hasExtra(Intents.Scan.CAMERA_ID)) {
          int cameraId = intent.getIntExtra(Intents.Scan.CAMERA_ID, -1);
          if (cameraId >= 0) {
            cameraManager.setManualCameraId(cameraId);
          }
        }
        
        String customPromptMessage = intent.getStringExtra(Intents.Scan.PROMPT_MESSAGE);
        if (customPromptMessage != null) {
          statusView.setText(customPromptMessage);
        }

      } else if (dataString != null &&
                 dataString.contains("http://www.google") &&
                 dataString.contains("/m/products/scan")) {

        // Scan only products and send the result to mobile Product Search.
        source = IntentSource.PRODUCT_SEARCH_LINK;
        sourceUrl = dataString;
        decodeFormats = DecodeFormatManager.PRODUCT_FORMATS;

      } else if (isZXingURL(dataString)) {

        // Scan formats requested in query string (all formats if none specified).
        // If a return URL is specified, send the results there. Otherwise, handle it ourselves.
        source = IntentSource.ZXING_LINK;
        sourceUrl = dataString;
        Uri inputUri = Uri.parse(dataString);
        scanFromWebPageManager = new ScanFromWebPageManager(inputUri);
        decodeFormats = DecodeFormatManager.parseDecodeFormats(inputUri);
        // Allow a sub-set of the hints to be specified by the caller.
        decodeHints = DecodeHintManager.parseDecodeHints(inputUri);

      }

      characterSet = intent.getStringExtra(Intents.Scan.CHARACTER_SET);

    }

    SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
    SurfaceHolder surfaceHolder = surfaceView.getHolder();
    if (hasSurface) {
      // The activity was paused but not stopped, so the surface still exists. Therefore
      // surfaceCreated() won't be called, so init the camera here.
      initCamera(surfaceHolder);
    } else {
      // Install the callback and wait for surfaceCreated() to init the camera.
      surfaceHolder.addCallback(this);
    }
  }

  private int getCurrentOrientation() {
    int rotation = getWindowManager().getDefaultDisplay().getRotation();
    switch (rotation) {
      case Surface.ROTATION_0:
      case Surface.ROTATION_90:
        return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
      default:
        return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
    }
  }
  
  private static boolean isZXingURL(String dataString) {
    if (dataString == null) {
      return false;
    }
    for (String url : ZXING_URLS) {
      if (dataString.startsWith(url)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void onPause() {
    if (handler != null) {
      handler.quitSynchronously();
      handler = null;
    }
    inactivityTimer.onPause();
    ambientLightManager.stop();
    beepManager.close();
    cameraManager.closeDriver();
    //historyManager = null; // Keep for onActivityResult
    if (!hasSurface) {
      SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
      SurfaceHolder surfaceHolder = surfaceView.getHolder();
      surfaceHolder.removeCallback(this);
    }
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    inactivityTimer.shutdown();
    super.onDestroy();
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_BACK:
        if (source == IntentSource.NATIVE_APP_INTENT) {
          setResult(RESULT_CANCELED);
          finish();
          return true;
        }
        if ((source == IntentSource.NONE || source == IntentSource.ZXING_LINK) && lastResult != null) {
          restartPreviewAfterDelay(0L);
          return true;
        }
        break;
      case KeyEvent.KEYCODE_FOCUS:
      case KeyEvent.KEYCODE_CAMERA:
        // Handle these events so they don't launch the Camera app
        return true;
      // Use volume up/down to turn on light
      case KeyEvent.KEYCODE_VOLUME_DOWN:
        cameraManager.setTorch(false);
        return true;
      case KeyEvent.KEYCODE_VOLUME_UP:
        cameraManager.setTorch(true);
        return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater menuInflater = getMenuInflater();
    menuInflater.inflate(R.menu.capture, menu);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
    switch (item.getItemId()) {
      case R.id.menu_upload:
    	try {
			UpLoadData();
		} catch (JSONException e) {
			// TODO 自动生成的 catch 块
			e.printStackTrace();
		}
    	break;
      case R.id.menu_help:
  		Toast.makeText(getApplicationContext(), "如您需要任何帮助，请联系李健.",Toast.LENGTH_LONG).show(); 
    	break;
      case R.id.menu_loginout:
  		LoginOut();
    	break;
      default:
        return super.onOptionsItemSelected(item);
    }
    return true;
  }
  private void 	LoginOut()
  {
	    DBManager db = new DBManager(this);
		db.DelUser();
		db.closeDB();
		UserName = "Admin";
		showWaiterAuthorizationDialog();
  }
  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    if (resultCode == RESULT_OK) {
    }
  }
  private void UpLoadData() throws JSONException
  {
	  //1. 检查wifi 状态
	  //2. 获取数据，构建json 
	  //3. 发送
	  //4. 收到回复后， 删除所有
      ShowWaitDialog("正在上传扫描数据，请稍等...");
	  if(!checkNetworkState())
	  {
		  IsComplete = true;
		  setNetwork();
		  return;
	  }
	  DBManager db = new DBManager(this);
	  List<CodeData> ValidCode = new ArrayList<CodeData>();
	  ValidCode = db.GetAllResult();
	  db.closeDB();
	  if(ValidCode.size()<=0)
	  {
		  IsComplete = true;
  		  Toast.makeText(getBaseContext(), "未发现有效数据，无需上传", Toast.LENGTH_LONG).show();
		  return;
	  }
	  //每5条记录为一个包
	  int DataSize = ValidCode.size();
	  int Start = 0;
	  SendOver = false;
	  while(DataSize>=5)
	  {
		  List<CodeData> Packet = ValidCode.subList(Start,Start+PacketSize);
		  String data = generateJson(Packet);
		  SocketSend(data);
		  ValidCode.subList(Start,Start+PacketSize).clear();
		  DataSize = ValidCode.size();
	  }
	  if(DataSize>0)
	  {
		  String data = generateJson(ValidCode);
		  SocketSend(data);
		  SendOver = true;
	  }
	  else if(DataSize == 0)
	  {
		  SendOver = true;
	  }
  }
  @SuppressLint("SimpleDateFormat") @SuppressWarnings("resource")
  private void SocketSend(final String data)
  {
      new Thread(new  Runnable() {
          @Override
          public void run() {
               
              try {
                  Socket socket=new Socket(InetAddress.getByName(Host), Port);
                  if(socket.equals(null))
                  {
        			  Message msg = Message.obtain();
    				  msg.what = 10;
    				  myHandler.sendMessage(msg);// 执行耗时的方法之后发送消给handler
                  }
                  OutputStream os=socket.getOutputStream();
                  os.write(data.getBytes("gbk"));
                  os.flush();
                  //防止服务端read方法读阻塞
                  socket.shutdownOutput();
                  //接收来自服务器的消息 
                  InputStream inputStream = socket.getInputStream();
                  DataInputStream input = new DataInputStream(inputStream);
                  byte[] b = new byte[10];
                  SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                  Date curDate = new Date(System.currentTimeMillis());
                  while(true)
                  {
                      Date endDate = new Date(System.currentTimeMillis());
                      if((endDate.getTime() - curDate.getTime())>1000*10)
                      {
                    	  break;
                      }
                      int length = input.read(b);
                      if(length>0)
                      {
                          String Msg = new String(b, 0, length,"UTF-8");
                          Msg = Msg.substring(0,2);
                          if(Msg.equals("OK")&&SendOver)
                          {
                        	  Msg = "";
                        	  SendOver = false;
              				  Message msg = Message.obtain();
            				  msg.what = 11;
            				  myHandler.sendMessage(msg);// 执行耗时的方法之后发送消给handler
                          }
                      }
                  }
                  socket.close();
                  os.close();
              }
              catch (IOException e) {
                  // TODO Auto-generated catch block
                  e.printStackTrace();
              }
          }
      }).start();
  }
  /**
	 * 检测网络是否连接
	 * @return
	 */
	private boolean checkNetworkState() {
		boolean flag = false;
		//得到网络连接信息
		ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	    State wifi = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
	    if(wifi == State.CONNECTED){
	    	flag = true;
	    }
		return flag;
    }
	/**
	 * 网络未连接时，调用设置方法
	 */
	private void setNetwork(){
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setIcon(R.drawable.ic_launcher);
		builder.setTitle("网络提示信息");
		builder.setMessage("网络不可用，请先设置网络！");
		builder.setPositiveButton("设置", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				Intent intent = null;
				/**
				 * 判断手机系统的版本！如果API大于10 就是3.0+
				 * 因为3.0以上的版本的设置和3.0以下的设置不一样，调用的方法不同
				 */
				if (android.os.Build.VERSION.SDK_INT > 10) {
					intent = new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS);
				} else {
					intent = new Intent();
					ComponentName component = new ComponentName(
							"com.android.settings",
							"com.android.settings.WirelessSettings");
					intent.setComponent(component);
					intent.setAction("android.intent.action.VIEW");
				}
				startActivity(intent);
			}
		});

		builder.setNegativeButton("取消", new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {

			}
		});
		builder.create();
		builder.show();
	}
	/* 显示等待对话框 */
	public void ShowWaitDialog(String context) {
		/* 显示ProgressDialog */
		pd = ProgressDialog.show(CaptureActivity.this, "", context,true);
		/* 开启一个新线程，在新线程里执行耗时的方法 */
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					spandTimeMethod();
				} catch (InterruptedException e) {
					// TODO 自动生成的 catch 块
					e.printStackTrace();
				}// 耗时的方法
				Message msg = Message.obtain();
				msg.what = 12;
				myHandler.sendMessage(msg);// 执行耗时的方法之后发送消给handler
			}

		}).start();
	}

	private void spandTimeMethod() throws InterruptedException {
		for(int i = 0; i< 60;i++)
		{
			if(IsComplete)
			{
				break;
			}
			Thread.sleep(1000);
		}
	}
  private void decodeOrStoreSavedBitmap(Bitmap bitmap, Result result) {
    // Bitmap isn't used yet -- will be used soon
    if (handler == null) {
      savedResultToShow = result;
    } else {
      if (result != null) {
        savedResultToShow = result;
      }
      if (savedResultToShow != null) {
        Message message = Message.obtain(handler, R.id.decode_succeeded, savedResultToShow);
        handler.sendMessage(message);
      }
      savedResultToShow = null;
    }
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    if (holder == null) {
      Log.e(TAG, "*** WARNING *** surfaceCreated() gave us a null surface!");
    }
    if (!hasSurface) {
      hasSurface = true;
      initCamera(holder);
    }
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    hasSurface = false;
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

  }

  /**
   * A valid barcode has been found, so give an indication of success and show the results.
   *
   * @param rawResult The contents of the barcode.
   * @param scaleFactor amount by which thumbnail was scaled
   * @param barcode   A greyscale bitmap of the camera data which was decoded.
   */
  public void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor) {
    inactivityTimer.onActivity();
    lastResult = rawResult;
    ResultHandler resultHandler = ResultHandlerFactory.makeResultHandler(this, rawResult);

    boolean fromLiveScan = barcode != null;
    if (fromLiveScan) {
      // Then not from history, so beep/vibrate and we have an image to draw on
      beepManager.playBeepSoundAndVibrate();
      drawResultPoints(barcode, scaleFactor, rawResult);
    }

    switch (source) {
      case NATIVE_APP_INTENT:
      case PRODUCT_SEARCH_LINK:
        handleDecodeExternally(rawResult, resultHandler, barcode);
        break;
      case ZXING_LINK:
        if (scanFromWebPageManager == null || !scanFromWebPageManager.isScanFromWebPage()) {
          handleDecodeInternally(rawResult, resultHandler, barcode);
        } else {
          handleDecodeExternally(rawResult, resultHandler, barcode);
        }
        break;
      case NONE:
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (fromLiveScan && prefs.getBoolean(PreferencesActivity.KEY_BULK_MODE, false)) {
          Toast.makeText(getApplicationContext(),
                         getResources().getString(R.string.msg_bulk_mode_scanned) + " (" + rawResult.getText() + ')',
                         Toast.LENGTH_SHORT).show();
          // Wait a moment or else it will scan the same barcode continuously about 3 times
          restartPreviewAfterDelay(BULK_MODE_SCAN_DELAY_MS);
        } else {
          handleDecodeInternally(rawResult, resultHandler, barcode);
        }
        break;
    }
  }

  /**
   * Superimpose a line for 1D or dots for 2D to highlight the key features of the barcode.
   *
   * @param barcode   A bitmap of the captured image.
   * @param scaleFactor amount by which thumbnail was scaled
   * @param rawResult The decoded results which contains the points to draw.
   */
  private void drawResultPoints(Bitmap barcode, float scaleFactor, Result rawResult) {
    ResultPoint[] points = rawResult.getResultPoints();
    if (points != null && points.length > 0) {
      Canvas canvas = new Canvas(barcode);
      Paint paint = new Paint();
      paint.setColor(getResources().getColor(R.color.result_points));
      if (points.length == 2) {
        paint.setStrokeWidth(4.0f);
        drawLine(canvas, paint, points[0], points[1], scaleFactor);
      } else if (points.length == 4 &&
                 (rawResult.getBarcodeFormat() == BarcodeFormat.UPC_A ||
                  rawResult.getBarcodeFormat() == BarcodeFormat.EAN_13)) {
        // Hacky special case -- draw two lines, for the barcode and metadata
        drawLine(canvas, paint, points[0], points[1], scaleFactor);
        drawLine(canvas, paint, points[2], points[3], scaleFactor);
      } else {
        paint.setStrokeWidth(10.0f);
        for (ResultPoint point : points) {
          if (point != null) {
            canvas.drawPoint(scaleFactor * point.getX(), scaleFactor * point.getY(), paint);
          }
        }
      }
    }
  }

  private static void drawLine(Canvas canvas, Paint paint, ResultPoint a, ResultPoint b, float scaleFactor) {
    if (a != null && b != null) {
      canvas.drawLine(scaleFactor * a.getX(), 
                      scaleFactor * a.getY(), 
                      scaleFactor * b.getX(), 
                      scaleFactor * b.getY(), 
                      paint);
    }
  }

  // Put up our own UI for how to handle the decoded contents.
  private void handleDecodeInternally(Result rawResult, ResultHandler resultHandler, Bitmap barcode) {

    CharSequence displayContents = resultHandler.getDisplayContents();

    if (copyToClipboard && !resultHandler.areContentsSecure()) {
      ClipboardInterface.setText(displayContents, this);
    }

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

    if (resultHandler.getDefaultButtonID() != null && prefs.getBoolean(PreferencesActivity.KEY_AUTO_OPEN_WEB, false)) {
      resultHandler.handleButtonPress(UserName,resultHandler.getDefaultButtonID());
      return;
    }

    statusView.setVisibility(View.GONE);
    viewfinderView.setVisibility(View.GONE);
    resultView.setVisibility(View.VISIBLE);

    ImageView barcodeImageView = (ImageView) findViewById(R.id.barcode_image_view);
    if (barcode == null) {
      barcodeImageView.setImageBitmap(BitmapFactory.decodeResource(getResources(),
          R.drawable.launcher_icon));
    } else {
      barcodeImageView.setImageBitmap(barcode);
    }
    //显示用户
    TextView formatTextView = (TextView) findViewById(R.id.format_text_view);
    formatTextView.setText(UserName);

    TextView typeTextView = (TextView) findViewById(R.id.type_text_view);
    typeTextView.setText(OperateType);

    DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
    TextView timeTextView = (TextView) findViewById(R.id.time_text_view);
    timeTextView.setText(formatter.format(new Date(rawResult.getTimestamp())));

    TextView contentsTextView = (TextView) findViewById(R.id.contents_text_view);
    contentsTextView.setText(displayContents);
    int scaledSize = Math.max(22, 32 - displayContents.length() / 4);
    contentsTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSize);

    //保存到数据库
    //计算时间差
    boolean IsRepeat = false;
    long value = 0;
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    Date curDate = new Date(System.currentTimeMillis());
    try {
		Date lastDate = df.parse(LastScanTime);
		value = curDate.getTime()-lastDate.getTime();
	} catch (ParseException e) {
		e.printStackTrace();
	}
    if(LastCode.equals(displayContents.toString())&&value <30*60*1000) //MS
    {
		Toast.makeText(getApplicationContext(), "重复扫描",Toast.LENGTH_LONG).show();
    	IsRepeat = true;
    }
    else if(displayContents.toString().length()>60)//检测Code 长度
    {
    	Toast.makeText(getApplicationContext(), "条形码或者二维码内容超长丢弃",Toast.LENGTH_LONG).show();
    }
    else
    {
    	//保存数据
    	DBManager db = new DBManager(this);
    	CodeData data = new CodeData();
    	data.Name = UserName;
    	data.Code = displayContents.toString();
    	data.Time = df.format(curDate);
    	data.Des  = "WIFI上传";
    	db.InsertCodeResult(data);
    	db.closeDB();
    	LastScanTime = df.format(curDate);
    	LastCode = displayContents.toString();
    }
    int buttonCount = resultHandler.getButtonCount();
    ViewGroup buttonView = (ViewGroup) findViewById(R.id.result_button_view);
    buttonView.requestFocus();
    for (int x = 0; x < ResultHandler.MAX_BUTTON_COUNT; x++) {
      TextView button = (TextView) buttonView.getChildAt(x);
      if (!IsRepeat && x < buttonCount) {
        button.setVisibility(View.VISIBLE);
        button.setText(resultHandler.getButtonText(x));
        button.setOnClickListener(new ResultButtonListener(UserName,resultHandler, x));
      } else {
        button.setVisibility(View.GONE);
      }
    }

  }

  // Briefly show the contents of the barcode, then handle the result outside Barcode Scanner.
  private void handleDecodeExternally(Result rawResult, ResultHandler resultHandler, Bitmap barcode) {

    if (barcode != null) {
      viewfinderView.drawResultBitmap(barcode);
    }

    long resultDurationMS;
    if (getIntent() == null) {
      resultDurationMS = DEFAULT_INTENT_RESULT_DURATION_MS;
    } else {
      resultDurationMS = getIntent().getLongExtra(Intents.Scan.RESULT_DISPLAY_DURATION_MS,
                                                  DEFAULT_INTENT_RESULT_DURATION_MS);
    }

    if (resultDurationMS > 0) {
      String rawResultString = String.valueOf(rawResult);
      if (rawResultString.length() > 32) {
        rawResultString = rawResultString.substring(0, 32) + " ...";
      }
      statusView.setText(getString(resultHandler.getDisplayTitle()) + " : " + rawResultString);
    }

    if (source == IntentSource.NATIVE_APP_INTENT) {
      
      // Hand back whatever action they requested - this can be changed to Intents.Scan.ACTION when
      // the deprecated intent is retired.
      Intent intent = new Intent(getIntent().getAction());
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
      intent.putExtra(Intents.Scan.RESULT, rawResult.toString());
      intent.putExtra(Intents.Scan.RESULT_FORMAT, rawResult.getBarcodeFormat().toString());
      byte[] rawBytes = rawResult.getRawBytes();
      if (rawBytes != null && rawBytes.length > 0) {
        intent.putExtra(Intents.Scan.RESULT_BYTES, rawBytes);
      }
      Map<ResultMetadataType,?> metadata = rawResult.getResultMetadata();
      if (metadata != null) {
        if (metadata.containsKey(ResultMetadataType.UPC_EAN_EXTENSION)) {
          intent.putExtra(Intents.Scan.RESULT_UPC_EAN_EXTENSION,
                          metadata.get(ResultMetadataType.UPC_EAN_EXTENSION).toString());
        }
        Number orientation = (Number) metadata.get(ResultMetadataType.ORIENTATION);
        if (orientation != null) {
          intent.putExtra(Intents.Scan.RESULT_ORIENTATION, orientation.intValue());
        }
        String ecLevel = (String) metadata.get(ResultMetadataType.ERROR_CORRECTION_LEVEL);
        if (ecLevel != null) {
          intent.putExtra(Intents.Scan.RESULT_ERROR_CORRECTION_LEVEL, ecLevel);
        }
        @SuppressWarnings("unchecked")
        Iterable<byte[]> byteSegments = (Iterable<byte[]>) metadata.get(ResultMetadataType.BYTE_SEGMENTS);
        if (byteSegments != null) {
          int i = 0;
          for (byte[] byteSegment : byteSegments) {
            intent.putExtra(Intents.Scan.RESULT_BYTE_SEGMENTS_PREFIX + i, byteSegment);
            i++;
          }
        }
      }
      sendReplyMessage(R.id.return_scan_result, intent, resultDurationMS);
      
    } else if (source == IntentSource.PRODUCT_SEARCH_LINK) {
      
      // Reformulate the URL which triggered us into a query, so that the request goes to the same
      // TLD as the scan URL.
      int end = sourceUrl.lastIndexOf("/scan");
      String replyURL = sourceUrl.substring(0, end) + "?q=" + resultHandler.getDisplayContents() + "&source=zxing";      
      sendReplyMessage(R.id.launch_product_query, replyURL, resultDurationMS);
      
    } else if (source == IntentSource.ZXING_LINK) {

      if (scanFromWebPageManager != null && scanFromWebPageManager.isScanFromWebPage()) {
        String replyURL = scanFromWebPageManager.buildReplyURL(rawResult, resultHandler);
        scanFromWebPageManager = null;
        sendReplyMessage(R.id.launch_product_query, replyURL, resultDurationMS);
      }
      
    }
  }
  
  private void sendReplyMessage(int id, Object arg, long delayMS) {
    if (handler != null) {
      Message message = Message.obtain(handler, id, arg);
      if (delayMS > 0L) {
        handler.sendMessageDelayed(message, delayMS);
      } else {
        handler.sendMessage(message);
      }
    }
  }

  private void initCamera(SurfaceHolder surfaceHolder) {
    if (surfaceHolder == null) {
      throw new IllegalStateException("No SurfaceHolder provided");
    }
    if (cameraManager.isOpen()) {
      Log.w(TAG, "initCamera() while already open -- late SurfaceView callback?");
      return;
    }
    try {
      cameraManager.openDriver(surfaceHolder);
      // Creating the handler starts the preview, which can also throw a RuntimeException.
      if (handler == null) {
        handler = new CaptureActivityHandler(this, decodeFormats, decodeHints, characterSet, cameraManager);
      }
      decodeOrStoreSavedBitmap(null, null);
    } catch (IOException ioe) {
      Log.w(TAG, ioe);
      displayFrameworkBugMessageAndExit();
    } catch (RuntimeException e) {
      // Barcode Scanner has seen crashes in the wild of this variety:
      // java.?lang.?RuntimeException: Fail to connect to camera service
      Log.w(TAG, "Unexpected error initializing camera", e);
      displayFrameworkBugMessageAndExit();
    }
  }

  private void displayFrameworkBugMessageAndExit() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(getString(R.string.app_name));
    builder.setMessage(getString(R.string.msg_camera_framework_bug));
    builder.setPositiveButton(R.string.button_ok, new FinishListener(this));
    builder.setOnCancelListener(new FinishListener(this));
    builder.show();
  }

  public void restartPreviewAfterDelay(long delayMS) {
    if (handler != null) {
      handler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
    }
    resetStatusView();
  }

  private void resetStatusView() {
    resultView.setVisibility(View.GONE);
    statusView.setText(R.string.msg_default_status);
    statusView.setVisibility(View.VISIBLE);
    viewfinderView.setVisibility(View.VISIBLE);
    lastResult = null;
  }

  public void drawViewfinder() {
    viewfinderView.drawViewfinder();
  }
  @SuppressLint("HandlerLeak") Handler myHandler = new Handler() {  
      @SuppressWarnings("deprecation")
		@Override
		public void handleMessage(android.os.Message msg) {  
          if (msg.what == 0)  
          {

          }
          else if(msg.what == 10)
          {
			  if (pd != null) {
					pd.dismiss();// 关闭ProgressDialog
			  }
		      showAlertDialog(3); //超时
			  IsComplete = false;
          }
          else if(msg.what == 11)
          {
        	  removeCode();
        	  showAlertDialog(2); //
          	  IsComplete = true;
          }
          else if(msg.what == 12)
          {
				if (pd != null) {
					pd.dismiss();// 关闭ProgressDialog
				}
				if(!IsComplete)
				{
					showAlertDialog(1); //超时
				}
				IsComplete = false;
          }
          super.handleMessage(msg);  
      };  
  };
  private void removeCode()
  {
	  DBManager db = new DBManager(this);
	  db.DelAll();
	  db.closeDB();
      restartPreviewAfterDelay(0L);
  }
	private void showAlertDialog(int type)
    {
		// 创建退出对话框
		AlertDialog isExit = new AlertDialog.Builder(this).create();
		// 设置对话框标题
		isExit.setTitle("温馨提示");
    	switch(type)
    	{
    	case 1:
    		// 设置对话框消息
    		isExit.setMessage("上传数据超时，请重试");
    		// 添加选择按钮并注册监听
    		isExit.setButton("确定", listener);
    		// 设置对话框消息
    		break;
    	case 2:
    		// 设置对话框消息
    		isExit.setMessage("恭喜您，上传数据成功");
    		// 添加选择按钮并注册监听
    		isExit.setButton("确定", listener);
    		// 设置对话框消息
    		break;
    	case 3:
    		// 设置对话框消息
    		isExit.setMessage("服务器未响应，上传失败");
    		// 添加选择按钮并注册监听
    		isExit.setButton("确定", listener);
    		// 设置对话框消息
    		break;
    	case 4:
    		// 设置对话框消息
    		break;
    	case 5:
    		// 设置对话框消息
    		break;
    	case 6:
    		// 设置对话框消息
    		break;
    	}
		// 显示对话框
		isExit.show();
    }
    /** 监听对话框里面的button点击事件 */
	DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
		public void onClick(DialogInterface dialog, int which) {
			switch (which) {
			case AlertDialog.BUTTON_POSITIVE:// "确认"按钮退出程序
				dialog.dismiss();
				break;
			case AlertDialog.BUTTON_NEGATIVE:// "取消"第二个按钮取消对话框
				break;
			default:
				break;
			}
		}
	};
	private String generateJson(List<CodeData> ValidList) throws JSONException
	{
		int len = 0;
		if(ValidList.size() == 0)
		{
			return "";
		}
	    JSONObject json=new JSONObject();  
	    JSONArray jsonMembers = new JSONArray();  
	    for(CodeData Result:ValidList)
	    {
	    	JSONObject member = new JSONObject();
	    	member.put("Name", Result.Name);  
	    	member.put("Code", Result.Code);  
	    	member.put("Time", Result.Time);  
	    	member.put("Des",Result.Des);   
	    	jsonMembers.put(member);
	    }
	    json.put("CodeInfo", jsonMembers);
	    return json.toString();
	}
	private String GetUserInfo()
	{
		DBManager db = new DBManager(this);
		String Name = db.GetUserName();
		db.closeDB();
	    return Name;
	}
}
