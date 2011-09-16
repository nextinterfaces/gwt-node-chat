package com.ywebb.chat.client;

import java.util.Date;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.http.client.URL;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.PopupPanel.PositionCallback;

public class Gwt_chat implements EntryPoint {

	int since = -1;
	VerticalPanel messagePanel = new VerticalPanel();

	public void onModuleLoad() {
		RootPanel root = RootPanel.get("root");
		VerticalPanel rootPanel = new VerticalPanel();
		root.add(rootPanel);
		createLoginDialog();
	}

	void setSince(int since) {
		this.since = since;
	}

	void createLoginDialog() {

		final DialogBox dialogBox = new DialogBox();
		dialogBox.setText("Welcome to my GWT Node Chat");
		dialogBox.setAnimationEnabled(true);

		final Button sendButton = new Button("Enter");
		final TextBox nameField = new TextBox();

		sendButton.addStyleName("sendButton");

		FlexTable table = new FlexTable();

		table.setWidget(0, 0, new HTML("Please choose a nickname"));
		table.setWidget(0, 1, nameField);
		table.setWidget(0, 2, sendButton);
		table.setCellPadding(10);
		dialogBox.setWidget(table);

		class LoginHandler implements ClickHandler, KeyUpHandler {
			public void onClick(ClickEvent event) {
				if (nameField.getText() != null && nameField.getText().trim().length() > 0) {
					doLogin(nameField.getText());
					dialogBox.hide();
				}
			}

			public void onKeyUp(KeyUpEvent event) {
				if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
					onClick(null);
				}
			}
		}

		LoginHandler handler = new LoginHandler();
		sendButton.addClickHandler(handler);
		nameField.addKeyUpHandler(handler);

		centerDialog(dialogBox);
	}

	void createChatDialog(final String id) {

		final DialogBox dialogBox = new DialogBox();
		dialogBox.setSize("390px", "300px");
		dialogBox.setText("Chatting..");
		dialogBox.setAnimationEnabled(true);

		final Button sendButton = new Button("Send");
		final TextArea inText = new TextArea();
		inText.setWidth("100%");

		sendButton.addStyleName("sendButton");

		FlexTable table = new FlexTable();
		table.setWidth("100%");
		ScrollPanel panel = new ScrollPanel(messagePanel);

		panel.setSize("100%", "300px");
		table.setWidget(0, 0, panel);
		table.setWidget(1, 0, inText);
		table.setWidget(1, 1, sendButton);
		FlexCellFormatter fc = table.getFlexCellFormatter();
		fc.setColSpan(0, 0, 2);
		table.setCellPadding(10);
		dialogBox.setWidget(table);

		class MessageHandler implements ClickHandler, KeyUpHandler {
			public void onClick(ClickEvent event) {
				String msg = inText.getText();
				inText.setText("");
				doSendMessage(msg, id);
			}

			public void onKeyUp(KeyUpEvent event) {
				if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
					onClick(null);
				}
			}
		}

		MessageHandler handler = new MessageHandler();
		sendButton.addClickHandler(handler);
		inText.addKeyUpHandler(handler);

		centerDialog(dialogBox);
	}

	void doLogin(String nick) {
		String url = "/chat/join?_=" + new Date().getTime() + "&nick=" + nick;
		makeHTTPCall(url, new OnCallBack() {
			@Override
			public void invoke(Response response) {
				JSONValue jsonValue = JSONParser.parse(response.getText());
				JSONObject obj = jsonValue.isObject();
				JSONValue idJSON = obj.get("id");
				String id = idJSON.toString();
				id = removeQoutes(id);

				JSONValue sinceJSON = obj.get("since");
				double since = sinceJSON.isNumber().doubleValue();
				setSince((int) since);

				createChatDialog(id);
				doLongPoll(id);
			}
		});
	}

	String removeQoutes(String msg) {
		return msg.substring(1, msg.length() - 1);
	}

	void centerDialog(final DialogBox dialogBox) {
		dialogBox.setPopupPositionAndShow(new PositionCallback() {
			@Override
			public void setPosition(int offsetWidth, int offsetHeight) {
				int leftStart = (Window.getClientWidth() - offsetWidth) / 2;
				int topEnd = (Window.getClientHeight() - offsetHeight) / 2;
				dialogBox.setPopupPosition(leftStart, topEnd);
			}
		});
	}

	void doSendMessage(String msg, String id) {

		String url = "/chat/send?_=" + new Date().getTime() + "&id=" + id + "&text=" + URL.encode(msg);

		makeHTTPCall(url, new OnCallBack() {
			@Override
			public void invoke(Response response) {
				JSONValue jsonValue = JSONParser.parse(response.getText());
				JSONObject obj = jsonValue.isObject();
				JSONValue idJSON = obj.get("id");
				double since = idJSON.isNumber().doubleValue();
				setSince((int) since);
			}
		});
	}

	void doLongPoll(final String id) {
		String url = "/chat/recv?_=" + new Date().getTime() + "&since=" + this.since + "&id=" + id;
		makeHTTPCall(url, new OnCallBack() {
			@Override
			public void invoke(Response response) {
				parseLongPoll(response);
				doLongPoll(id);
			}
		});
	}

	void parseLongPoll(Response resp) {
		if (!resp.getText().contains("id")) {
			return;
		}
		// {"messages":[
		// {"id":15,"nick":"aaaaaa","type":"msg","text":"ll","timestamp":1290808023123},
		// {"id":15,"nick":"aaaaaa","type":"msg","text":"ll","timestamp":1290808023123}]}

		// {"messages":[
		// {"id":1,"nick":"aaaaaaaa","type":"join","timestamp":1290817002025},
		// {"id":2,"nick":"nnn","type":"join","timestamp":1290817056174}]}

		// 200response '{"messages":[{"id":5,"nick":"Stephen","type":"part","timestamp":1290818044872}]}

		JSONValue jsonValue = JSONParser.parse(resp.getText());
		JSONObject obj = jsonValue.isObject();
		JSONArray messagesJSON = obj.get("messages").isArray();
		for (int i = 0; i < messagesJSON.size(); i++) {
			JSONObject arrVal = messagesJSON.get(i).isObject();
			double since = arrVal.get("id").isNumber().doubleValue();
			setSince((int) since);

			boolean isJoin = arrVal.toString().contains("join"); // quick hack
			boolean isLeft = arrVal.toString().contains("part"); // quick hack
			try {
				String nick = arrVal.get("nick").isString().toString();
				nick = removeQoutes(nick);
				String message;

				if (isJoin) {
					message = "<em>joined the room</em>";
				} else if (isLeft) {
					message = "<em>left the room</em>";
				} else {
					message = arrVal.get("text").isString().toString();
					message = removeQoutes(message);
				}

				messagePanel.add(new HTML("<strong>" + nick + "</strong>: " + URL.decode(message)));

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	void makeHTTPCall(String url, final OnCallBack onCallBack) {

		RequestBuilder builder = new RequestBuilder(RequestBuilder.GET, URL.encode(url));

		try {
			builder.sendRequest(null, new RequestCallback() {
				public void onError(Request request, Throwable exception) {
					Window.alert("exception:" + exception);
				}

				public void onResponseReceived(Request request, Response response) {
					if (200 == response.getStatusCode()) {
						onCallBack.invoke(response);
					} else {
						Window.alert("ERROR: " + response.getStatusCode() + "response '" + response.getText() + "'");
					}
				}
			});
		} catch (RequestException e) {
			// TODO
		}
	}

	interface OnCallBack {
		void invoke(Response response);
	}

}
