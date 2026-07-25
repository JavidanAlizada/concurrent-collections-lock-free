# Treiber Stack

`TreiberStack<T>` — a lock-free LIFO stack backed by a singly linked list,
where `head` is only ever changed by a single compare-and-set.

## The algorithm

```
push(value):
  newNode = Node(value)
  loop:
    oldHead = head            // VarHandle.getVolatile
    newNode.next = oldHead    // linked locally, not yet visible to anyone
    if CAS(head, oldHead, newNode): return
    // someone else won the race — retry with the current head

pop():
  loop:
    oldHead = head
    if oldHead == null: return null   // empty
    newHead = oldHead.next
    if CAS(head, oldHead, newHead): return oldHead.value
    // retry
```

`peek()` and `isEmpty()` are single reads of `head`, no loop needed.
`size()` walks the list and counts — see "Weakly consistent size" below.

## Linearization points

- **push** linearizes at its successful CAS. Nothing before it (the node
  allocation, the `next` assignment) is observable by any other thread —
  only the CAS publishes the new node.
- **pop** linearizes at its successful CAS, or immediately at the `oldHead ==
  null` check if the stack is observed empty.

Both operations retry the entire loop on a failed CAS, so a thread never
returns based on stale data — it only returns once its own CAS (or its own
empty-check) has taken effect.

## Progress guarantee: lock-free, not wait-free

On any contended round, at least one thread's CAS succeeds — the *system*
always makes progress. What it doesn't guarantee is a per-thread bound: under
sufficiently adversarial scheduling, one specific thread could keep losing
the CAS race indefinitely while other threads succeed around it. No thread
ever blocks or holds a lock, though — the only way to "stall" is by retrying,
and a retry by definition means another thread just made progress.

## Memory model

`head` is a plain (non-`volatile`) field, deliberately accessed only through
explicit `VarHandle` calls — `HEAD.getVolatile(this)` for reads,
`HEAD.compareAndSet(this, oldHead, newHead)` for the mutation — rather than
declaring the field `volatile` and relying on implicit semantics. Both give
identical guarantees here; the explicit form was chosen so the access mode is
visible at each call site instead of hidden in a field modifier, which is the
whole reason this project uses `VarHandle` in the first place (see the repo
README).

A successful `compareAndSet` has full volatile write semantics: it
establishes happens-before with every subsequent `getVolatile` read of `head`
by any thread. That's what makes a newly pushed node — and its `value` field
— safely visible to whichever thread pops it next.

`Node.next` is a plain field, not volatile, and that's intentional: it's
written exactly once, before the node is ever published via CAS, and never
mutated afterward. This is a **safe publication** argument, not a
synchronization one — the publishing CAS is what makes `next` visible, the
same way it makes `value` visible. There's no independent write to `next`
that needs its own ordering guarantee.

## Contention characteristics

Every `push` and every `pop`, regardless of thread count, contends on the
exact same memory location: `head`. This is true sharing, not false sharing
— the contention is inherent to a single-CAS-point algorithm, not an
artifact of memory layout. Expect the CAS retry rate to climb and throughput
to plateau (or degrade) as thread count increases past the number of
available cores; see `docs/benchmarks/` once Milestone 1's JMH results land
for actual measured numbers — this doc states the mechanism, not a
performance claim.

## Weakly consistent `size()`

`size()` is an O(n) traversal at call time, not a maintained counter. Under
concurrent mutation it's an estimate, not a linearizable snapshot — the same
trade-off `ConcurrentLinkedQueue.size()` makes, for the same reason: an
auxiliary atomic counter would mean every push/pop pays for a second CAS
just to keep a number in sync, for a method most callers use for rough
sizing, not exact accounting. `isEmpty()` doesn't have this problem — a
single `head == null` read is a valid, exact instant-in-time answer.

See also `docs/design/aba-problem.md` for why the ABA problem doesn't apply
to this implementation.
