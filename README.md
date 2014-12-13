<H2>Twitter API Proxy</H2>

API management gateway for Twitter search and tweet posting API. The gateway provided simple user authentication with username and password, user activity is tracked once they log in. All the request sent by user, errors from twitter API and errors from Proxy are written in a database, you can query this database and analyse gateway usage.

<h2>Tools used</h2>
1. Eclipse
2. SQLite browser http://sourceforge.net/projects/sqlitebrowser/

<H2>Running the Proxy</H2>
Pull the source code and run the file ClientOperations.java, the application by default listens on port 8080 and uses local sqlite database file to write searches and errors. Port can be changed in main method of ClientOperations.java and db configuration in dbfile.properties.

<H2>Database tables info</H2>
1. checkaccess - for checking rate limit for user
2. erros - stores error sent by Twitter API
3. ipbanlist - list of IP addresses banned from accessing the proxy
4. proxy errors - saved all errors sent by proxy
5. searches - saves all searches done by user
6. users - consist user information

<H2>Twitter API documentation</H2>
1. Twitter search API documentation https://dev.twitter.com/rest/reference/get/search/tweets</br>
2. Twitter status update API documentation https://dev.twitter.com/rest/reference/post/statuses/update        
The Proxy supports all parameters mentioned in official documentation of respective API

<H2>Types of search</H2>
<b>general</b> - http://localhost:8080/twitter-search/general?parameter=value&parameter=value
<br>
<b>trends</b> - http://localhost:8080/twitter-search/trends?parameter=value&parameter=value
<h2>Posting tweet</h2>
<b>post tweet</b> - http://localhost:8080/twitter-proxy/posttweet?parameter=value&parameter=value
<h2>Proxy analytics</h2>
<b>API analytics</b> - http://localhost:8080/twitter-proxy/apianalytics?parameter=value&parameter=value
<br>
<b>user analytics</b> - http://localhost:8080/twitter-proxy/useranalytics?parameter=value&parameter=value
<h2>Proxy utilities</h2>
<b>Add user</b> - http://localhost:8080/twitter-proxy/addnewuser?parameter=value&parameter=value
<br>
<b>IP utilities</b> - http://localhost:8080/twitter-proxy/iputil?parameter=value&parameter=value
