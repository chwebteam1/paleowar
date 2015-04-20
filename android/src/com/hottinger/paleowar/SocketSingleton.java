package com.hottinger.paleowar;

import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;

public class SocketSingleton {

	private static SocketSingleton instance;
	private static final String SERVER_ADDRESS = "http://192.168.43.138";
	private SocketIO socket;
	private Context context;

	public static SocketSingleton get(Context context) {
		if (instance == null) {
			instance = getSync(context);
		}
		instance.context = context;
		return instance;
	}

	public static synchronized SocketSingleton getSync(Context context) {
		if (instance == null) {
			instance = new SocketSingleton(context);
		}
		return instance;
	}

	public SocketIO getSocket() {
		return this.socket;
	}

	private SocketSingleton(Context context) {
		this.context = context;
		this.socket = getChatServerSocket();
		// this.friends = new ArrayList<Friend>();
	}

	private SocketIO getChatServerSocket() {
		try {
			SocketIO socket = new SocketIO(new URL(SERVER_ADDRESS), new IOCallback() {
				@Override
				public void onDisconnect() {
					System.out.println("disconnected");
				}

				@Override
				public void onConnect() {
					System.out.println("connected");
				}

				@Override
				public void on(String event, IOAcknowledge ioAcknowledge, Object... objects) {
					if (event.equals("chatMessage")) {
						/* JSONObject json = (JSONObject) objects[0];
						ChatMessage chatMessage = new ChatMessage(json);

						Intent intent = new Intent();
						intent.setAction("newChatMessage");
						intent.putExtra("chatMessage", chatMessage);
						context.sendBroadcast(intent); */
					}
				}

				@Override
				public void onError(SocketIOException e) {
					e.printStackTrace();
				}

				@Override
				public void onMessage(String arg0, IOAcknowledge arg1) {
					// TODO Auto-generated method stub

				}

				@Override
				public void onMessage(JSONObject arg0, IOAcknowledge arg1) {
					// TODO Auto-generated method stub

				}
			});
			return socket;
		} catch (MalformedURLException ex) {
			ex.printStackTrace();
		}
		return null;
	}
}
