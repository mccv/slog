# Slog - a Java GC log parser

This is a very very preliminary attempt at writing a comprehensive Java gc log parser using [parboiled](https://github.com/sirthias/parboiled/wiki).
Right now the parser works, but
* there are almost certainly bugs and formats it won't parse
* stat aggregation/output is crude at best

However. You should be able to run this via sbt (0.11.x) with

        sbt "run -f <yourfile>"

Or build a jarfile to execute...

# Building

You can build an executable jar with

        sbt one-jar

This gets dumped into target/scala-2.9.2/....

# Usage

java -jar slog.jar

        Usage: slog [options]

          -f <value> | --file <value>
                log file to parse
          --from <value>
                time since process start in seconds to start from
          --to <value>
                time since process start in seconds to parse to
          -p <value> | --period <value>
                period of stats (in seconds)
          -g <value> | --generations <value>
                gc generations to report statistics for
          --print-detailed <value>
                print detailed statistics
          --print-cpu-times <value>
                print cpu times for runs
          --print-allocations <value>
                print allocation statistics

# Next Steps

* Add ability to print stat summaries
* Report on CMS activity
* Parser a wider range of log files, as there are definitely gaps in the parser now
* More flexibility with output format/visualization