# About
Linkshortener service consists of two endpoints. First creates short link from the original link. Second redirects to the original link. All calls of the second endpoint correspond to link clicks. Click events are persisted.

# Dev
To start app you must create `conf/application-local.conf` file and add JVM option `-Dconfig.resource=application-local.conf`. You will find sample `conf` file at `conf/application-local.conf.sample`.