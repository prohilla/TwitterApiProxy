package com.api.proxy;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.scribe.model.Response;

import com.api.util.ProxyRequest;
import com.api.util.TwitterOauth;

/**
 * Class with main method and business logic
 * @author PVR
 *
 */
class ClientOperations implements Runnable {

	// For SQLite database
	private static final String dbProperties = "dbfile.properties";
	private static final String URL = "url";

	//Twitter API PATHS
	private static final String TWITTER_API_SEARCH = "https://api.twitter.com/1.1/search/tweets.json?";
	private static final String TWITTER_API_TRENDS = "https://api.twitter.com/1.1/trends/place.json?";
	private static final String TWITTER_API_POST = "https://api.twitter.com/1.1/statuses/update.json?";

	//Proxy API PATHS
	private static final String PROXY_TWITTER_SEARCH = "/twitter-search/general";
	private static final String PROXY_TWITTER_TRENDS = "/twitter-search/trends";
	private static final String PROXY_ADDUSER = "/twitter-proxy/addnewuser";
	private static final String API_ANALYTICS = "/twitter-proxy/apianalytics";
	private static final String USER_ANALYTICS = "/twitter-proxy/useranalytics";
	private static final String PROXY_POST_TWEET = "/twitter-proxy/posttweet";
	private static final String PROXY_IP_UTIL = "/twitter-proxy/iputil";

	// RATE limit per hour per user
	private static final int RATE_LIMIT = 10;
	
	// Search types
	private static final String GENERAL_SEARCH = "general";
	private static final String TREND_SEARCH = "trends";

	// Setting HTTP response HEADER
	private static final String HTTP_RESPONSE = "HTTP/1.0 200 OK";

	//Proxy user parameters
	private static final String USER_NAME = "username";
	private static final String USER_NAME_ANALYTICS = "user";
	private static final String USER_PASSWORD = "password";
	private static final String NEW_USER_NAME = "newusername";
	private static final String NEW_USER_PASSWORD = "newuserpassword";

	// URL parameter for twitter search API
	private static final String QUERY = "q";
	private static final String GEO_CODE = "geocode";
	private static final String LANGUAGE = "lang";
	private static final String LOCALE = "locale";
	private static final String RESULT_TYPE = "result_type";
	private static final String COUNT = "count";
	private static final String UNTIL = "until";
	private static final String SINCEID = "since_id";
	private static final String MAXID = "max_id";
	private static final String INCLUDEENT = "include_entities";
	private static final String CALLBACK = "callback";
	private static final String PLACEID = "id";
	private static final String EXCLUDE = "exclude";

	// SQL Queries for adding and authenticating user
	private static final String ADD_NEW_USER = "INSERT INTO users (sessionid, username, password) VALUES (?, ?, ?);";
	private static final String AUTHENTICATE_USER = "SELECT password FROM users WHERE username = ? LIMIT 1;";

	// SQL Queries for saving search results, IP address checking and obtaining analytics data
	private static final String INSERT_SEARCH_DATABASE = "INSERT INTO searches (sessionid, timestamp, searchtype, username, query, geocode, language, locale, result_type, "
			+ "count, until, since_id, max_id, include_entities, callback, placeid, exclude) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
	private static final String INSERT_ERROR_DATABASE = "INSERT INTO errors (sessionid, timestamp, searchtype, username, status, apiendpoint, apimessage, apierrorcode, proxypath) "
			+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";
	private static final String CHECK_IP_EXSIST = "SELECT * FROM checkAccess where ipaddress= ? LIMIT 1;";
	private static final String RESET_ACCESS = "UPDATE checkaccess SET accessno=1, timestamp=DateTime('now') WHERE ipaddress= ? ;";
	private static final String UPDATE_ACCESS = "UPDATE checkaccess SET accessno=accessno+1 WHERE ipaddress= ? ;";
	private static final String INSERT_ACCESS = "INSERT INTO checkaccess (accessno, ipaddress) VALUES (?, ?);";
	private static final String CHECK_TIME_DIFFERENCE = "SELECT ((julianday('now') - julianday(timestamp))*24) AS hours from checkaccess where ipaddress= ?;";
	private static final String INSERT_PROXY_ERROR_DATABASE = "INSERT INTO proxyerrors (status, message) VALUES (?, ?);";
	private static final String FETCH_USER_ANALYTICS = "SELECT searchtype, COUNT (*) as countno FROM searches where username= ? GROUP BY searchtype;";
	private static final String FETCH_API_ANALYTICS = "SELECT searchtype, COUNT(*) as countno FROM searches GROUP BY searchtype;";
	private static final String BAN_IP = "INSERT INTO ipbanlist (ipaddress) VALUES (?);";
	private static final String UNBAN_IP = "DELETE FROM ipbanlist where ipaddress= ?;";
	private static final String CHECK_IP = "SELECT * FROM ipbanlist where ipaddress= ?;";

	//Error fields for JSON response
	private static final String RESPONSE_RESULTS = "response_results";
	private static final String RESPONSE_ERROR_MESSAGE = "response_error_message";
	private static final String RESPONSE_STATUS = "response-status";
	private static final String PROXY_REQUEST_INVALID = "PROXY_REQUEST_INVALID";
	private static final String PROXY_INVALID_URL = "PROXY_URL_INVALID";
	private static final String PROXY_AUTHENTICATION_FAILED = "PROXY_AUTHENTICATION_FAILED";
	private static final String PROXY_SEARCH_ERROR = "PROXY_SEARCH_ERROR";
	private static final String PROXY_ANALYTICS_ERROR = "PROXY_ANALYTICS_ERROR";
	private static final String PROXY_ADD_NEW_USER_ERROR = "PROXY_ADD_NEW_USER_ERROR";
	private static final String ACCESS_LIMIT_REACHED = "ACCESS_LIMIT_REACHED";
	private static final String NO_DATA_T0_SHOW = "NO_DATA_TO_SHOW_YET";
	private static final String OK = "OK";
	
	//Initializing logger
	private final Logger logger = Logger.getLogger(ClientOperations.class
			.getName());
	
	private final Socket socket;
	private final UUID sessionId;
	private final long timestamp;
	private final String dbUrl;
	private final Properties properties;

	public ClientOperations(Socket socket, long timestamp, String url,
			Properties properties) {
		this.socket = socket;
		this.sessionId = UUID.randomUUID();
		this.timestamp = timestamp;
		this.dbUrl = url;
		this.properties = properties;
	}

	@Override
	public void run() {
		try {
			//Initializing input and output streams for scoket
			BufferedReader in = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			
			//Checking for valid request
			String line = in.readLine();
			String[] httpRequest = line != null ? line.split(" ") : null;
			if (httpRequest == null
					|| httpRequest.length != 3
					|| !httpRequest[0].equals("GET")
					|| (!httpRequest[2].equals("HTTP/1.0") && !httpRequest[2]
							.equals("HTTP/1.1"))) {
				errorResponse(PROXY_REQUEST_INVALID,
						"This provided request is not a valid HTTP request.",
						out);
				return;
			}
			
			ProxyRequest request = new ProxyRequest(httpRequest[1]);
			
			//Cheking if IP is banned
			if (checkBan(socket.getInetAddress().getHostAddress())) {
				errorResponse(
						"IP_BANNED",
						"Your IP address is banned, please contact administrator.",
						out);
				return;
			}
			
			//Cheking if IP has reached RATE limit
			if (!checkAccess(socket.getInetAddress().getHostAddress())) {
				errorResponse(
						ACCESS_LIMIT_REACHED,
						"Access limit has been reached, only "+RATE_LIMIT+" request allowed per hour.",
						out);
				return;
			}


			//Authenticating user
			String username = request.get(USER_NAME);
			String password = request.get(USER_PASSWORD) == null ? "password"
					: "pasword";
			if (!validateUser(username, password)) {
				errorResponse(PROXY_AUTHENTICATION_FAILED,
						"The provided credentials failed authentication.", out);
				return;
			}

			//Decide which API function to call
			switch (request.getUrlPath()) {

			case PROXY_TWITTER_SEARCH:
				twitterSearch(request, GENERAL_SEARCH, TWITTER_API_SEARCH, out);
				break;

			case PROXY_TWITTER_TRENDS:
				twitterSearch(request, TREND_SEARCH, TWITTER_API_TRENDS, out);
				break;

			case PROXY_POST_TWEET:
				postTweet(request, TWITTER_API_POST, out);
				break;

			case API_ANALYTICS:
				apiAnalytics(request, out);
				break;

			case USER_ANALYTICS:
				userAnalytics(request, out);
				break;

			case PROXY_ADDUSER:
				addNewUser(request, out);
				break;

			case PROXY_IP_UTIL:
				ipUtil(request, out);
				break;

			default:
				errorResponse(PROXY_INVALID_URL,
						"The provided URL is unsupported or invalid.", out);
				break;
			}

		} catch (IOException e) {
			logger.log(Level.WARNING, "Error while reading client request", e);
			return;
		} catch (Exception e) {
			logger.log(Level.WARNING, "Unknown error while handling client request",
					e);
			return;
		} finally {
			try {
				if (socket != null) {
					socket.close();
				}
			} catch (IOException e) {
				logger.log(Level.WARNING, "Exception during closing socket", e);
			}
		}
	}
	
	/**
	 * Check if IP address is banned
	 * @param hostAddress IP address of client
	 * @return True if IP is Banned
	 */
	private boolean checkBan(String hostAddress) {
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet result = null;

		try {
			connection = DriverManager.getConnection(dbUrl, properties);
			statement = connection.prepareStatement(CHECK_IP);
			statement.setObject(1, hostAddress);
			result = statement.executeQuery();

			if (result.next()) {
				return true;
			}
		} catch (Exception e) {
			
			logger.log(Level.WARNING, "Error while performing ip actions.", e);
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException e) {
				logger.log(Level.WARNING,
						"Exception when closing database resources", e);
			}
		}
		return false;
	}
	
	/**
	 * IP utility to ban, unban IP address
	 * @param request the client request
	 * @param out scoket output stream
	 * @return
	 */
	private void ipUtil(ProxyRequest request, PrintWriter out) {

		Connection connection = null;
		PreparedStatement statement = null;
		PreparedStatement statement1 = null;
		PreparedStatement statement2 = null;
		ResultSet result = null;
		String state = "state";

		try {
			connection = DriverManager.getConnection(dbUrl, properties);
			statement = connection.prepareStatement(CHECK_IP);
			statement.setObject(1, request.get("ip"));
			result = statement.executeQuery();

			if (request.get("action").equals("ban") && result.next()) {
				state = "IP_ALREADY_BANNED";
			} else if (request.get("action").equals("ban") && !result.next()) {
				statement1 = connection.prepareStatement(BAN_IP);
				statement1.setObject(1, request.get("ip"));
				statement1.executeUpdate();
				state = "IP_BANNED";
			} else if (request.get("action").equals("unban") && result.next()) {
				statement2 = connection.prepareStatement(UNBAN_IP);
				statement2.setObject(1, request.get("ip"));
				statement2.executeUpdate();
				state = "IP_UNBANNED";
			} else if (request.get("action").equals("unban") && !result.next()) {
				state = "IP_NOT_PRESENT_AS_BANNED";
			}
			emptyResponse(state, out);
		} catch (Exception e) {
			
			logger.log(Level.WARNING, "Error while performing ip actions.", e);
			errorResponse("ERRO_WHILE_IP_ACTIONS",
					"Error while performing ip actions.", out);
		} finally {
			try {
				if (statement != null && statement1 != null
						&& statement2 != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException e) {
				logger.log(Level.WARNING,
						"Exception when closing database resources", e);
			}
		}
	}
	
	/**
	 * Post tweets to twitter handle @prashroh
	 * @param request
	 * @param apiPath
	 * @param out
	 */
	private void postTweet(ProxyRequest request, String apiPath,
			PrintWriter out) {

		TwitterOauth te = null;
		String resource = apiPath + request.getUrlQuery();
		try {
			te = new TwitterOauth(resource, "post");
			Response response = te.getSignedRequest().send();
			BufferedReader in = new BufferedReader(new InputStreamReader(
					response.getStream()));
			String line;
			
			//Reading the headers from API response
			Map<String, String> map = response.getHeaders();
			out.write(map.get(null) + "\r\n");
			map.remove(null);
			
			//Forwarding Headers to client
			for (Map.Entry<String, String> entry : map.entrySet()) {
				out.write(entry.getKey() + ": " + entry.getValue() + "\r\n");
			}
			out.write("\r\n");
			
			//Forwarding the body to client
			while ((line = in.readLine()) != null) {
				out.println(line);
			}

		} catch (Exception e) {
			
			logger.log(Level.WARNING, "ERROR while posting new tweet", e);
			errorResponse(PROXY_SEARCH_ERROR, "Posting to twitter failed.", out);
			return;
		}

		return;
	}
	
	/**
	 * Get user analytics
	 * @param request the client request
	 * @param out the socket output stream
	 */
	private void userAnalytics(ProxyRequest request, PrintWriter out) {
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet result = null;
		Map<String, Integer> map = new HashMap<String, Integer>();
		try {

			
			connection = DriverManager.getConnection(dbUrl, properties);
			statement = connection.prepareStatement(FETCH_USER_ANALYTICS);
			statement.setString(1, request.get(USER_NAME_ANALYTICS));
			result = statement.executeQuery();
			while (result.next()) {
				map.put(result.getString("searchtype"),
						result.getInt("countno"));
			}
			if (!map.isEmpty()) {
				JSONObject obj = new JSONObject();
				JSONArray jray = new JSONArray();
				jray.put(map);
				obj.put("user analytics", jray);
				out.println(HTTP_RESPONSE);
				out.println();
				out.println(obj.toString());
			} else {
				emptyResponse(NO_DATA_T0_SHOW, out);
				return;
			}

		} catch (Exception e) {
			
			logger.log(Level.WARNING, "Error performing USER analytics", e);
			errorResponse(PROXY_ANALYTICS_ERROR,
					"USER analytics could not be completed.", out);
		}

	}

	/**
	 * Get proxy API analytics
	 * @param request the client request
	 * @param out socket output stream
	 */
	private void apiAnalytics(ProxyRequest request, PrintWriter out) {
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet result = null;
		Map<String, Integer> map = new HashMap<String, Integer>();
		try {

			
			connection = DriverManager.getConnection(dbUrl, properties);
			statement = connection.prepareStatement(FETCH_API_ANALYTICS);
			result = statement.executeQuery();
			while (result.next()) {
				map.put(result.getString("searchtype"),
						result.getInt("countno"));
			}
			if (!map.isEmpty()) {
				JSONObject obj = new JSONObject();
				JSONArray jray = new JSONArray();
				jray.put(map);
				obj.put("api analytics", jray);
				out.println(HTTP_RESPONSE);
				out.println();
				out.println(obj.toString());
			} else {
				emptyResponse(NO_DATA_T0_SHOW, out);
				return;
			}

		} catch (Exception e) {
			
			logger.log(Level.WARNING, "Error performing API analytics", e);
			errorResponse(PROXY_ANALYTICS_ERROR,
					"API analytics could not be completed.", out);
		}

	}

	/**
	 * Perform twitter search
	 * @param request the client request
	 * @param searchType the type of search client performed
	 * @param apiPath the api path of proxied API
	 * @param out socket output stream
	 */
	private void twitterSearch(ProxyRequest request, String searchType,
			String apiPath, PrintWriter out) {
		TwitterOauth te = null;
		String resource = apiPath + request.getUrlQuery();
		String responseStaus;
		try {
			te = new TwitterOauth(resource, "get");
			Response response = te.getSignedRequest().send();
			BufferedReader in = new BufferedReader(new InputStreamReader(
					response.getStream()));
			String line;
			StringBuilder newline = new StringBuilder();
			
			//Reading the headers from API response
			Map<String, String> map = response.getHeaders();
			responseStaus = map.get(null);
			out.write(map.get(null) + "\r\n");
			map.remove(null);
			
			//Forwarding the headers to client
			for (Map.Entry<String, String> entry : map.entrySet()) {
				out.write(entry.getKey() + ": " + entry.getValue() + "\r\n");
			}
			out.write("\r\n");
			
			//Forwarding the body to client
			while ((line = in.readLine()) != null) {
				out.println(line);
				newline.append(line);
			}
			//Writing search to database
			if (dbUrl != null && responseStaus.equals("HTTP/1.1 200 OK")) {
				writeSearch(request, searchType);
			} else if (dbUrl != null
					&& !responseStaus.equals("HTTP/1.1 200 OK")) {
				writeError(request, searchType, newline, responseStaus);
			}

		} catch (Exception e) {
			
			logger.log(Level.WARNING, "Error performing twitter " + searchType
					+ " search.", e);
			errorResponse(PROXY_SEARCH_ERROR,
					"The Twitter Search could not be completed.", out);
		}

	}
	
	/**
	 * Add new user to proxy
	 * @param request client request
	 * @param out socket output stream
	 */
	private void addNewUser(ProxyRequest request, PrintWriter out) {
		Connection connection = null;
		PreparedStatement statement = null;

		try {

			
			connection = DriverManager.getConnection(dbUrl, properties);
			statement = connection.prepareStatement(ADD_NEW_USER);
			statement.setObject(1, sessionId);
			statement.setString(2, request.get(NEW_USER_NAME));
			statement.setString(3, request.get(NEW_USER_PASSWORD));
			statement.executeUpdate();

			// Write a response to client
			emptyResponse(OK, out);

		} catch (Exception e) {
			
			logger.log(Level.WARNING, "Error adding new user to database", e);
			errorResponse(
					PROXY_ADD_NEW_USER_ERROR,
					"The new user could not be added to the database.  A user with the same username may already exist.",
					out);
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException e) {
				logger.log(Level.WARNING,
						"Exception when closing database resources", e);
			}
		}
	}

	/**
	 * Check rate limit for client
	 * @param ip the client ip adress
	 * @return ture if client has access and withing rate limit
	 */
	public boolean checkAccess(String ip) {

		Connection connection = null;
		PreparedStatement statement = null;
		PreparedStatement statement1 = null;
		PreparedStatement statement2 = null;
		ResultSet result = null;

		try {

			connection = DriverManager.getConnection(dbUrl, properties);
			statement = connection.prepareStatement(CHECK_IP_EXSIST);
			statement.setObject(1, ip);
			result = statement.executeQuery();
			if (result.next()) {
				int check = checkDate(ip);
				if (check > 1) {
					statement1 = connection.prepareStatement(RESET_ACCESS);
					statement1.setObject(1, ip);
					statement1.executeUpdate();
					return true;
				} else if (check < 1 && result.getInt("accessno") < RATE_LIMIT) {
					statement2 = connection.prepareStatement(UPDATE_ACCESS);
					statement2.setObject(1, ip);
					statement2.executeUpdate();
					return true;
				} else if (check < 1 && result.getInt("accessno") > RATE_LIMIT) {
					return false;
				}
			} else {
				statement2 = connection.prepareStatement(INSERT_ACCESS);
				statement2.setObject(1, 1);
				statement2.setObject(2, ip);
				statement2.executeUpdate();
				return true;
			}
		} catch (Exception e) {
			logger.log(Level.WARNING, "Error while checking access for RATE limit", e);
			return false;
		} finally {
			try {
				if (statement != null && statement1 != null
						&& statement2 != null) {
					statement.close();
					statement1.close();
					statement2.close();
				}
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException e) {
				logger.log(Level.WARNING,
						"Exception when closing database resources", e);
			}
		}
		return false;
	}

	/**
	 * Check time difference
	 * @param ip client ip address
	 * @return number of hours lapsed from first access to now
	 */
	public int checkDate(String ip) {

		int totalhours = 0;
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet result = null;

		try {

			connection = DriverManager.getConnection(dbUrl, properties);
			statement = connection.prepareStatement(CHECK_TIME_DIFFERENCE);
			statement.setObject(1, ip);
			result = statement.executeQuery();
			if (result.next()) {
				totalhours = result.getInt("hours");
			}
		} catch (Exception e) {
			
			logger.log(Level.WARNING, "Error checking time difference", e);
			return totalhours;
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException e) {
				logger.log(Level.WARNING,
						"Exception when closing database resources", e);
			}
		}
		return totalhours;
	}

	/**
	 * Authenticate client
	 * @param username client username
	 * @param password client password
	 * @return true if client successfully validated
	 */
	private boolean validateUser(String username, String password) {
		if (username == null) {
			return unAuthenticatedUsers();
		}

		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet result = null;

		try {
			
			connection = DriverManager.getConnection(dbUrl, properties);
			statement = connection.prepareStatement(AUTHENTICATE_USER);
			statement.setString(1, username);
			result = statement.executeQuery();

			if (result.next()) {
				if (password.equals(result.getString("password"))) {
					return true;
				}
			}

		} catch (SQLException e) {
			logger.log(Level.WARNING,
					"Error authenticating user info with database", e);
			return false;
		} finally {
			try {
				if (result != null) {
					result.close();
				}
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException e) {
				logger.log(Level.WARNING,
						"Exception when closing database resources", e);
			}
		}

		return false;
	}

	/**
	 * Write searches to database
	 * @param request client request
	 * @param searchType the type of search client performed
	 */
	private void writeSearch(ProxyRequest request, String searchType) {
		Connection connection = null;
		PreparedStatement statement = null;
		try {
			
			connection = DriverManager.getConnection(dbUrl, properties);
			
			statement = connection.prepareStatement(INSERT_SEARCH_DATABASE);
			statement.setObject(1, sessionId);
			statement.setTimestamp(2, new Timestamp(timestamp));
			statement.setString(3, searchType);
			statement.setString(4, request.get(USER_NAME));
			statement.setString(5, request.get(QUERY));
			statement.setString(6, request.get(GEO_CODE));
			statement.setString(7, request.get(LANGUAGE));
			statement.setString(8, request.get(LOCALE));
			statement.setString(9, request.get(RESULT_TYPE));
			statement.setString(10, request.get(COUNT));
			statement.setString(11, request.get(UNTIL));
			statement.setString(12, request.get(SINCEID));
			statement.setString(13, request.get(MAXID));
			statement.setString(14, request.get(INCLUDEENT));
			statement.setString(15, request.get(CALLBACK));
			statement.setString(16, request.get(PLACEID));
			statement.setString(17, request.get(EXCLUDE));
			statement.executeUpdate();

		} catch (SQLException e) {
			logger.log(Level.WARNING, "Error writing search to database", e);
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException e) {
				logger.log(Level.WARNING,
						"Exception when closing database resources", e);
			}
		}
	}

	/**
	 * 
	 * @param request the client request
	 * @param searchType the type of search client performed
	 * @param line error from proxied API
	 * @param response response from proxied API
	 * @return
	 */
	private boolean writeError(ProxyRequest request, String searchType,
			StringBuilder line, String response) {
		Connection connection = null;
		PreparedStatement statement = null;
		JSONObject jsonResponse = new JSONObject(line.toString());
		JSONArray jray = jsonResponse.getJSONArray("errors");
		JSONObject jobj = jray.getJSONObject(0);
		try {
			connection = DriverManager.getConnection(dbUrl, properties);
			statement = connection.prepareStatement(INSERT_ERROR_DATABASE);
			statement.setObject(1, sessionId);
			statement.setTimestamp(2, new Timestamp(timestamp));
			statement.setString(3, searchType);
			statement.setString(4, request.get(USER_NAME));
			statement.setString(5, response);
			if (searchType.equals(GENERAL_SEARCH)) {
				statement.setString(6, TWITTER_API_SEARCH);
				statement.setString(9, PROXY_TWITTER_SEARCH);
			} else if (searchType.equals(TREND_SEARCH)) {
				statement.setString(6, TWITTER_API_TRENDS);
				statement.setString(9, PROXY_TWITTER_TRENDS);
			}
			statement.setString(7, jobj.getString("message"));
			statement.setInt(8, jobj.getInt("code"));
			statement.executeUpdate();

		} catch (SQLException e) {
			logger.log(Level.WARNING, "Error writing error to database", e);
			return false;
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException e) {
				logger.log(Level.WARNING,
						"Exception when closing database resources", e);
			}
		}

		return true;
	}

	/**
	 * Send empty response whenever needed
	 * @param status Status Message
	 * @param out socket output stream
	 */
	private static void emptyResponse(String status, PrintWriter out) {
		JSONObject jsonResponse = new JSONObject();

		jsonResponse.put(RESPONSE_STATUS, status);
		jsonResponse.put(RESPONSE_RESULTS, new JSONArray());

		out.println(HTTP_RESPONSE);
		out.println();
		out.println(jsonResponse.toString(3));
	}

	/**
	 * Send error response to client
	 * @param status Error Status
	 * @param message Error Message
	 * @param out socket output stream
	 */
	private void errorResponse(String status, String message, PrintWriter out) {
		JSONObject jsonResponse = new JSONObject();

		jsonResponse.put(RESPONSE_STATUS, status);
		jsonResponse.put(RESPONSE_ERROR_MESSAGE, message);
		jsonResponse.put(RESPONSE_RESULTS, new JSONArray());
		writeProxyErrorToDatabase(status, message);

		out.println(HTTP_RESPONSE);
		out.println();
		out.println(jsonResponse.toString(3));
	}

	/**
	 * Write error reponse to database
	 * @param status2 Error Status
	 * @param message Error Message
	 */
	private void writeProxyErrorToDatabase(String status2, String message) {
		Connection connection = null;
		PreparedStatement statement = null;
		try {
			connection = DriverManager.getConnection(dbUrl, properties);
			statement = connection
					.prepareStatement(INSERT_PROXY_ERROR_DATABASE);
			statement.setObject(1, status2);
			statement.setObject(2, message);
			statement.executeUpdate();

		} catch (SQLException e) {
			logger.log(Level.WARNING, "Error writing proxy error to database",
					e);
		} finally {
			try {
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}
			} catch (SQLException e) {
				logger.log(Level.WARNING,
						"Exception when closing database resources", e);
			}
		}
	}
	
	/**
	 * Allow unauthenticated users
	 * @return true if unauthenticated users are allowed to access proxy
	 */
	private static boolean unAuthenticatedUsers() {
		return true;
	}

	/**
	 * Main method to run proxy
	 * @param args
	 */
	public static void main(String[] args) {
		Properties properties = new Properties();
		String dbUrl = null;

		try {
			properties.load(new FileInputStream(dbProperties));
			dbUrl = properties.getProperty(URL);

		} catch (IOException e) {
			System.err.format(
					"Error reading database connection info from %s",
					dbProperties);
			System.err.flush();
			e.printStackTrace();
		}

		Proxy proxy = new Proxy(properties, dbUrl, 8080);
		proxy.start();
	}
}