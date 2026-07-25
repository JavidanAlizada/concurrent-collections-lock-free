# The ABA problem

## What it is, in general

A CAS only checks that a memory location still holds the value it expects —
it can't tell whether that value changed and changed back in between. Classic
example on a lock-free stack: thread T reads `head == A`, then stalls. Another
thread pops A, pops B, then pushes a *new* node that happens to reuse address
A (common with pooled/recycled nodes or certain allocators). T resumes, sees
`head == A`, and its CAS succeeds — but it's now linking against a
completely different logical node than the one it read. The stack ends up
corrupted even though every individual CAS "succeeded."

## Why it doesn't apply to `TreiberStack` as implemented

Every `push` allocates a brand-new `Node` via `new Node<>(value)`. Nodes are
never pooled, recycled, or reused — once popped, a node is simply eligible
for garbage collection like any other unreachable object. The JVM guarantees
that a live object's identity is unique for as long as it's reachable, and a
freed object's address can't be handed back out to a *new* object while a
stale reference to the old one (`oldHead` in a paused thread's local
variable) still exists and is being compared against — the GC won't collect
an object that's still reachable from a thread's stack, and `oldHead` on a
paused thread's stack is exactly that: a GC root keeping the old node alive
until that thread's CAS attempt resolves one way or the other.

So the classic "address got reused with different meaning" scenario can't
happen here: as long as a thread holds `oldHead`, the object it points to
stays exactly what it was when read. If `head` really did change and change
back to the *same* object, that would mean the same node got pushed, popped,
and pushed again — which never happens either, since nodes aren't reused
across `push` calls.

## When this would need revisiting

Only if a future optimization introduces node pooling — reusing `Node`
instances across pop/push cycles to reduce allocation pressure. That would
reintroduce the address-reuse scenario above and require a real mitigation
(a tagged/versioned reference, `AtomicStampedReference`-style, or a hazard-
pointer scheme). That optimization is explicitly **not** being made
preemptively — per the project's own performance discipline, allocation rate
would need to show up as an actual bottleneck in `docs/benchmarks/` first.
Right now there's no evidence it is one, so the simpler, ABA-immune baseline
stands.
