package scalaz.stream

import scalaz.concurrent.{Strategy, Task}
import scalaz.stream.Process.halt
import scalaz.stream.async.mutable._
import scalaz.stream.merge.{Junction, JunctionStrategies}

/**
 * Created by pach on 03/05/14.
 */
package object async {

  /**
   * Creates bounded queue that is bound by supplied max size bound.
   * Please see [[scalaz.stream.async.mutable.Queue]] for more details.
   * @param max maximum size of queue. When <= 0 (default) queue is unbounded
   */
  def boundedQueue[A](max: Int = 0)(implicit S: Strategy): Queue[A] =
    Queue[A](max)


  /**
   * Creates unbounded queue. see [[scalaz.stream.async.mutable.Queue]] for more
   */
  def unboundedQueue[A](implicit S: Strategy): Queue[A] =  boundedQueue(0)

  /**
   * Create a source that may be added to or halted asynchronously
   * using the returned `Queue`. See `async.Queue`. As long as the
   * `Strategy` is not `Strategy.Sequential`, enqueueing is
   * guaranteed to take constant time, and consumers will be run on
   * a separate logical thread. 
   */
  @deprecated("Use async.unboundedQueue instead", "0.5.0")
  def queue[A](implicit S: Strategy) : (Queue[A], Process[Task, A]) = {
   val q = unboundedQueue[A]
    (q,q.dequeue)
  }

  /**
   * Create a new continuous signal which may be controlled asynchronously.
   * All views into the returned signal are backed by the same underlying
   * asynchronous `Ref`.
   */
  def signal[A](implicit S: Strategy): Signal[A] =
    Signal(halt, haltOnSource = false)

  /**
   * Converts discrete process to signal. Note that, resulting signal must be manually closed, in case the
   * source process would terminate (see `haltOnSource`).
   * However if the source terminate with failure, the signal is terminated with that
   * failure
   * @param source          discrete process publishing values to this signal
   * @param haltOnSource    closes the given signal when the `source` terminates
   */
  def toSignal[A](source: Process[Task, A], haltOnSource: Boolean = false)(implicit S: Strategy): mutable.Signal[A] =
    Signal(source.map(Signal.Set(_)), haltOnSource)

  /**
   * A signal constructor from discrete stream, that is backed by some sort of stateful primitive
   * like an Topic, another Signal or queue.
   *
   * If supplied process is normal process, it will, produce a signal that eventually may
   * be de-sync between changes, continuous, discrete or changed variants
   *
   * @param source
   * @tparam A
   * @return
   */
  private[stream] def stateSignal[A](source: Process[Task, A])(implicit S:Strategy) : immutable.Signal[A] =
    new immutable.Signal[A] {
      def changes: Process[Task, Unit] = discrete.map(_ => ())
      def continuous: Process[Task, A] = discrete.wye(Process.constant(()))(wye.echoLeft)(S)
      def discrete: Process[Task, A] = source
      def changed: Process[Task, Boolean] = (discrete.map(_ => true) merge Process.constant(false))
    }

  /**
   * Returns a topic, that can create publisher (sink) and subscriber (source)
   * processes that can be used to publish and subscribe asynchronously.
   * Please see `Topic` for more info.
   */
  def topic[A](source: Process[Task, A] = halt, haltOnSource: Boolean = false)(implicit S: Strategy): Topic[A] = {
    val wt = WriterTopic[Nothing, A, A](Process.liftW(process1.id))(source, haltOnSource = haltOnSource)(S)
    new Topic[A] {
      def publish: Sink[Task, A] = wt.publish
      def subscribe: Process[Task, A] = wt.subscribeO
      def publishOne(a: A): Task[Unit] = wt.publishOne(a)
      def fail(err: Throwable): Task[Unit] = wt.fail(err)
    }
  }

  /**
   * Returns Writer topic, that can create publisher(sink) of `I` and subscriber with signal of `W` values.
   * For more info see `WriterTopic`.
   * Note that when `source` ends, the topic does not terminate
   */
  def writerTopic[W, I, O](w: Writer1[W, I, O])(source: Process[Task, I] = halt, haltOnSource: Boolean = false)
    (implicit S: Strategy): WriterTopic[W, I, O] =
    WriterTopic[W, I, O](w)(source, haltOnSource = haltOnSource)(S)


}
