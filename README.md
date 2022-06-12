# Thread Pool

A thread pool implementation in Java which tried to overcome one of the major shortcoming 
(in the author's opinion): reusing idle threads.
There is actually a long-standing JDK enhancement about this [issue](https://bugs.openjdk.org/browse/JDK-6452337).

The use case for this feature is the following: when a process requires to have many thread pools, each
with a significant number of maximum (core) threads, we end up having (many) hundreds of threads laying around which
will consume resources (memory). 

Every task scheduled with the pool in the JDK implementation ends up creating
a thread even if there are idle threads lying around. These threads will not be reclaimed until
the timeout for core threads is reached (if enabled).

In addition to this specific (but important) enhancement, the thread pool aims to provide various feature to help writing multithread applications easier:
* TODO