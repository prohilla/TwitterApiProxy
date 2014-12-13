# Twitter API Proxy
API management gateway for Twitter search API. The gateway provided simple user authentication with username and password, user activity is tracked once they log in. All the request sent by user, errors from twitter API and errors from Proxy are written in a database, you can query this database and analyse the gateway usage.

### What all can be done ?
* Client can perform Twitter search with all parameters supported as with the official Twitter search API.
* Client can get Twitter trends with all parameters supported as with the official Twitter trends API.
* User can authenticate himself with proxy using username and password. New users can be added to proxy. Ratelimits can be imposed and IP addresses can be banned & unbanned.
* Analytics can be obtained for proxy and user.

### Running the proxy

Pull the source code and run file ClientOperations.java, the application by default listens on port 8080 and uses local sqlite database file to write searches and errors. Port can be changed in main method of ClientOperations.java and db configuration in dbfile.properties.

### Tools used
- Eclipse
- SQLite browser

### Database and tables

The file name **twitterdb** in db folder is the SQLite database file to and from which the application writes and reads. The **dbfile.properties** is used for loading configuration to application.

- <b>checkaccess</b> - for checking rate limit for user
-  <b>errors</b> - stores error sent by Twitter API
- <b>ipbanlist</b> - list of IP addresses banned from accessing the proxy
- <b>proxyerrors</b> - saved all errors sent by proxy
- <b>searches</b> - saves all searches done by users
- <b>users</b> - consist user information

### Twitter API documentation
1. Twitter search API documentation
>https://dev.twitter.com/rest/reference/get/search/tweets
2. Twitter get trends by place
>https://dev.twitter.com/rest/reference/get/trends/place

The Proxy supports all parameters mentioned in official documentation of respective API

### Proxy user authentication
```
http://localhost:8080/twitter-search/<whatever>?username=value&password=value&parameter=value&parameter=value
```
Proxy authentication  is optional and by default turned off by method unAuthenticatedUsers() in ClientOperations.java file. You can force user authentication by changing return value true to false in this method. User activity can only be tracked once they log in ie. use above method of sending request.

### Proxy search url
```
http://localhost:8080/twitter-search/general?parameter=value&parameter=value 
```
##### Sample search url

```
http://localhost:8080/twitter-search/general?q=california&result_type=recent&count=10&until=2014-12-06
```
### Proxy get trends
```
http://localhost:8080/twitter-search/trend?parameter=value&parameter=value 
```
##### Sample get trends url
```
http://localhost:8080/twitter-search/trends?id=23424977&exclude=hashtags
```
### Proxy analytics
Returns the number of times the search proxy (general) and trends proxy has been accesed.
```
http://localhost:8080/twitter-proxy/apianalytics
```
### User analytics
Returns the proxy api name and number of times this user has accessed it.
```
http://localhost:8080/twitter-proxy/useranalytics?user=value
```
### Proxy utilities

<b>Add new user to proxy</b>
```
http://localhost:8080/twitter-proxy/addnewuser?newusername=value&newuserpassword=value
```
<b>Ban or Unban IP address</b>
```
http://localhost:8080/twitter-proxy/iputil?ip=value&action=ban
```
```
http://localhost:8080/twitter-proxy/iputil?ip=value&action=unban
```
