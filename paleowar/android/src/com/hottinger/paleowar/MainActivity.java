package com.hottinger.paleowar;

import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.phychips.rcp.RcpApi;
import com.phychips.rcp.RcpException;
import com.phychips.rcp.RcpLib;
import com.phychips.rcp.iRcpEvent;

public class MainActivity extends Activity implements iRcpEvent, IOnHandlerMessage {
	public static final String KEY_ENCODING = "my_encoding";
	public static final String KEY_SAVELOG = "my_saveLog";
	public static final String KEY_flag = "my_message";

	public static boolean saveLog = false;
	public static int max_tag = 0, max_time = 0, repeat_cycle = 0, encoding_type = 0;
	private String temp, test_msg;

	private int battery;

	private Handler m_Handler;
	private Button btnReady;

	private ArrayList<String> tagArray = new ArrayList<String>();
	private ArrayList<seqTag> seqArray = new ArrayList<seqTag>();

	@SuppressWarnings("unused")
	private boolean beepState, headsetConnected = false, bUpdateRequired = false;

	@SuppressWarnings("unused")
	private int m_VolumeBackup;

	// private SharedPreferences pref;
	static private boolean m_bOnCreate = false;
	static boolean flag;

	int[] m_data;

	private SocketIO socket;

	HeadsetConnectionReceiver m_Headset = null;

	@SuppressLint("CommitPrefEdits")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);

		Spinner spinner = (Spinner) findViewById(R.id.idPlayer);

		SharedPreferences prefs = getSharedPreferences("PrefName", MODE_PRIVATE);
		// @SuppressWarnings("unused")
		SharedPreferences.Editor editor = prefs.edit();

		encoding_type = prefs.getInt(KEY_ENCODING, 0);
		saveLog = prefs.getBoolean(KEY_SAVELOG, false);
		flag = prefs.getBoolean(KEY_flag, false);

		if (!m_bOnCreate) {
			m_bOnCreate = true;
			m_Headset = new HeadsetConnectionReceiver();
			registerReceiver(m_Headset, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
		}

		m_Handler = new Handler();
		try {
			// setVolumeMax();
			RcpApi.open();
			m_Handler.sendEmptyMessage(8);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		btnReady = (Button) findViewById(R.id.btnReady);

		btnReady.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				System.out.println("----------------------> " + test());
				Spinner idPlayer = (Spinner) findViewById(R.id.idPlayer);
				EditText pseudo = (EditText) findViewById(R.id.pseudoPlayer);
				JSONObject infoPlayer;
				try {
					infoPlayer = new JSONObject("{\"idPlayer\":\"" + idPlayer.getSelectedItem().toString() + "\", \"pseudo\":\"" + pseudo.getText().toString() + "\"}");
					// socket.send(infoPlayer);
					socket.emit("Ready", infoPlayer);
					idPlayer.setEnabled(false);
					pseudo.setEnabled(false);
					System.out.println(infoPlayer);
				} catch (JSONException e) {
					e.printStackTrace();
				}

				/* if (RcpApi.isOpen) {
					try {
						RcpApi.startReadTags(max_tag, max_time, repeat_cycle);
					} catch (RcpException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else {
					Toast.makeText(MainActivity.this, "Reader  not Opened", Toast.LENGTH_SHORT).show();
				} */

			}
		});

		try {
			socket = new SocketIO("http://192.168.43.138:3000");
		} catch (MalformedURLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		socket.connect(new IOCallback() {
			@Override
			public void onMessage(JSONObject json, IOAcknowledge ack) {
				try {
					System.out.println("Server said:" + json.toString(2));
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}

			@Override
			public void onMessage(String data, IOAcknowledge ack) {
				System.out.println("Server said: " + data);
			}

			@Override
			public void onError(SocketIOException socketIOException) {
				System.out.println("an Error occured");
				socketIOException.printStackTrace();
			}

			@Override
			public void onDisconnect() {
				System.out.println("Connection terminated.");
			}

			@Override
			public void onConnect() {
				System.out.println("Connection established");
			}

			@Override
			public void on(String event, IOAcknowledge ack, Object... args) {
				System.out.println("Server triggered event '" + event + "'");
				System.out.println("args "+args[0].toString());
				// Toast.makeText(MainActivity.this, "MY Tag is : "+args[0].toString(), Toast.LENGTH_SHORT).show();
				/* if (event.equals("ReturnReady")) {
					Toast.makeText(MainActivity.this, "MY Tag is : "+args.toString(), Toast.LENGTH_SHORT).show();
					System.out.println("MY Tag is : "+args.toString());
				} */
			}
		});
	}

	// -------------------------------------------------------------end
	// MainActivity onCreate

	// Headset Connection
	public class HeadsetConnectionReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context arg0, Intent intent) {
			// TODO Auto-generated method stub
			if (intent.hasExtra("state")) {
				if (intent.getIntExtra("state", 0) == 0) {
					m_Handler.sendEmptyMessage(5);
				} else if (intent.getIntExtra("state", 0) == 1) {
					m_Handler.sendEmptyMessage(6);
				}
			}
		}
	}

	public void startCapture() {
		long time = System.currentTimeMillis();
		Calendar c = Calendar.getInstance();
		c.setTimeInMillis(time);

		String fileName;

		fileName = "POPLog_" + Build.MODEL + "_" + Integer.toString(c.get(Calendar.YEAR)) + Integer.toString(c.get(Calendar.MONTH) + 101).substring(1)
				+ Integer.toString(c.get(Calendar.DATE) + 100).substring(1) + Integer.toString(c.get(Calendar.HOUR_OF_DAY) + 100).substring(1)
				+ Integer.toString(c.get(Calendar.MINUTE) + 100).substring(1) + Integer.toString(c.get(Calendar.SECOND) + 100).substring(1) + ".csv";

		this.writeFile(fileName);
	}

	private FileOutputStream m_fos = null;

	private void writeFile(String filename) {

		try {
			File file = new File(Environment.getExternalStorageDirectory().getPath() + "/ARETE/", filename);
			m_fos = new FileOutputStream(file);
			DataOutputStream ostream = new DataOutputStream(m_fos);
			for (int i = 0; i < seqArray.size(); i++) {
				String temp_tag = seqArray.get(i).getTag();
				String temp_count = seqArray.get(i).getCount();

				try {
					ostream.writeBytes(temp_tag + ",");
					ostream.writeBytes(temp_count + "\n");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			m_fos.flush();
			ostream.flush();
			m_fos.close();
			ostream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public static Intent createIntent(Context context) {
		Intent i = new Intent(context, MainActivity.class);
		i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		return i;
	}

	// Resume setRcpEvent- mainActivity
	// RcpOpen check

	@Override
	protected void onResume() {
		super.onResume();
		RcpApi.setRcpEvent(this);
		if (RcpApi.isOpen) {
			m_Handler.sendEmptyMessage(8);
		}
	}

	// Stop - not need
	@Override
	protected void onStop() {
		super.onStop();
		SharedPreferences prefs = getSharedPreferences("PrefName", MODE_PRIVATE);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(KEY_ENCODING, encoding_type);
		editor.putBoolean(KEY_SAVELOG, saveLog);
		editor.putBoolean(KEY_flag, flag);
		editor.commit();

	}

	@Override
	protected void onPause() {
		super.onPause();

	}

	@Override
	public void setRequestedOrientation(int requestedOrientation) {
		// TODO Auto-generated method stub
		super.setRequestedOrientation(requestedOrientation);
	}

	// Destroy - clearCache
	@Override
	protected void onDestroy() {

		if (isFinishing()) {
			try {
				if (RcpApi.isOpen == true) {
					RcpApi.close();
				}

				if (m_Headset != null) {
					unregisterReceiver(m_Headset);
					m_Headset = null;
				}

				m_bOnCreate = false;

			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		super.onDestroy();
	}

	// volumax now Voulume backup and volume Max
	private void setVolumeMax() {

		AudioManager AudioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);

		m_VolumeBackup = AudioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);

		AudioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, AudioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC), 1);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_VOLUME_DOWN:
		case KeyEvent.KEYCODE_VOLUME_UP:
			break;
		default:
			break;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onTagReceived(int[] datas) {
		m_data = datas;
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				TextView score = (TextView) findViewById(R.id.score);

				score.setText(RcpLib.int2str(m_data));
			}
		});
	}

	@Override
	public void onRegionReceived(int[] data) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSelectParamReceived(int[] data) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onQueryParamReceived(int[] data) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onChannelReceived(int[] data) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onFhLbtReceived(int[] data) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onTxPowerLevelReceived(int[] data) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onTagMemoryReceived(int[] data) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onHoppingTableReceived(int[] data) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onModulationParamReceived(int[] data) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onAnticolParamReceived(int[] data) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onTempReceived(int[] data) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onRssiReceived(int[] data) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onRegistryItemReceived(int[] data) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSuccessReceived(int[] data) {
		// TODO Auto-generated method stub
		m_Handler.sendEmptyMessage(1);
	}

	@Override
	public void onReaderInfoReceived(int[] data) {
		// TODO Auto-generated method stub
		temp = RcpLib.int2str(data);

		if (temp.substring(2, 4).equals("01")) {
			beepState = true;
		} else {
			beepState = false;
		}

	}

	@Override
	public void onFailureReceived(int[] data) {
		// TODO Auto-generated method stub
		test_msg = RcpLib.int2str(data);
		m_Handler.sendEmptyMessage(2);
	}

	@Override
	public void onResetReceived(int[] data) {
		// TODO Auto-generated method stub
		m_Handler.sendEmptyMessage(3);
	}

	@Override
	public void onAuthenticat(int[] arg0) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onBeepStateReceived(int[] arg0) {
		// TODO Auto-generated method stub
	}

	//
	@Override
	public void handlerMessage(Message msg) {
		// TODO Auto-generated method stub
		switch (msg.what) {
		case 0:
			// epclist.setVisibility(1);
			// epclist.setVisibility(0);
			bUpdateRequired = false;
			break;

		case 1:
			if (flag) {
				Toast.makeText(MainActivity.this, "Success", Toast.LENGTH_SHORT).show();
			}
			break;

		case 2:
			Toast.makeText(MainActivity.this, "Error code  :  " + test_msg, Toast.LENGTH_SHORT).show();
			if (flag) {
				Toast.makeText(MainActivity.this, "Failure", Toast.LENGTH_SHORT).show();
			}
			break;

		case 3:
			if (flag) {
				Toast.makeText(MainActivity.this, "Reader Opened", Toast.LENGTH_SHORT).show();
			}
			break;

		case 4:
			bUpdateRequired = false;
			break;

		case 5:
			headsetConnected = false;

			Toast.makeText(MainActivity.this, "Headset plug out", Toast.LENGTH_SHORT).show();
			break;

		case 6:
			headsetConnected = true;

			Toast.makeText(MainActivity.this, "Headset plug in", Toast.LENGTH_SHORT).show();
			break;

		case 7:
			break;

		case 8:
			try {
				RcpApi.getReaderInfo((byte) 0xB0);
			} catch (RcpException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			break;

		case 9:
			break;

		default:
			break;
		} // End switch

	}

	@Override
	public void onAdcReceived(int[] dest) {
		// TODO Auto-generated method stub
		temp = RcpLib.int2str(dest);

		String now = temp.substring(0, 2);
		String min = temp.substring(2, 4);
		String max = temp.substring(4, 6);

		if ((Integer.parseInt(max, 16) - Integer.parseInt(min, 16)) != 0) {
			battery = (Integer.parseInt(now, 16) - Integer.parseInt(min, 16)) * 100 / (Integer.parseInt(max, 16) - Integer.parseInt(min, 16));
		} else {
			battery = 0;
		}

		// clamping 0 <= battery <= 100
		if (battery > 100) {
			battery = 100;
		}

		if (battery < 0) {
			battery = 0;
		}
		m_Handler.sendEmptyMessage(7);
	}

	@Override
	public void onTestFerPacketReceived(int[] dest) {
		// TODO Auto-generated method stub

	}

	public boolean test() {
		ConnectivityManager cm = (ConnectivityManager) getSystemService(this.CONNECTIVITY_SERVICE);
		NetworkInfo ni = cm.getActiveNetworkInfo();
		if (null == ni)
			return false;
		return ni.isConnectedOrConnecting();
	}

	public boolean connect() {

		/* try {
			socket.connect(, new IOCallback() {

				@Override
				public void onMessage(JSONObject json, IOAcknowledge ack) {
					try {
						System.out.println("Server said:" + json.toString(2));
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}

				@Override
				public void onMessage(String data, IOAcknowledge ack) {
					System.out.println("Server said: " + data);
				}

				@Override
				public void onError(SocketIOException socketIOException) {
					System.out.println("an Error occured");
					socketIOException.printStackTrace();
				}

				@Override
				public void onDisconnect() {
					System.out.println("Connection terminated.");
				}

				@Override
				public void onConnect() {
					System.out.println("Connection established");
				}

				@Override
				public void on(String event, IOAcknowledge ack, Object... args) {
					System.out.println("Server triggered event '" + event + "'");
				}
			});
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} */

		return socket.isConnected();

	}

}
