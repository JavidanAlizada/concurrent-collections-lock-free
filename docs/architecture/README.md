# Architecture

System context, component structure, data flow, and dependency map for this
library. For a collection of independent data structures, "architecture" is
lighter-weight than for a service — this covers how the structures relate to
each other (shared VarHandle/memory-ordering utilities, if any emerge) and how
the module is meant to be consumed.

Populated as structures land, starting with Treiber stack in Milestone 1.
