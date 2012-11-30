# Slog - a Java GC log parser

This is a very very preliminary attempt at writing a comprehensive Java gc log parser using [parboiled](https://github.com/sirthias/parboiled/wiki).
Right now the parser works, but
* there are almost certainly bugs and formats it won't parse
* stat aggregation/output is crude at best
* command line args are nonexistent.

However. You should be able to run this via sbt (0.11.x) with

        sbt "run <yourfile>"

# Goals

I'd like to add command line options to output the following

* rate at which memory is being allocated
* rate at which objects are moving between eden -> survivor -> tenured spaces
* percentage of time the JVM is in stop the world
* how much time are we spending in collection
