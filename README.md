<H2>Twitter API Proxy</H2>

API management gateway for Twitter search and tweet posting API. The gateway provided simple user authentication with username and password, user activity is tracked once they log in. All the request sent by user, errors from twitter API and errors from Proxy are written in a database, you can query this database and analyse gateway usage.

<h2>Tools used</h2>
1. Eclipse
2. SQLite browser http://sourceforge.net/projects/sqlitebrowser/

<H2>Running the Proxy</H2>
Pull the source code and run the file ClientOperations.java, the application by default listens on port 8080 and uses local sqlite database file to write searches and errors. Port can be changed in main method of ClientOperations.java and db configuration in dbfile.properties.

<H2>Database description</H2>

The databse has 6 tables
1. checkaccess - for checking rate limit for user
2. erros - stores error sent by Twitter API
3. ipbanlist - list of IP addresses banned from accessing the proxy
4. proxy errors - saved all errors sent by proxy
5. searches - saves all searches done by user
6. users - consist user information
