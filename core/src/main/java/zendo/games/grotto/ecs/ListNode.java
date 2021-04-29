package zendo.games.grotto.ecs;

import com.badlogic.gdx.utils.Pool;

public abstract class ListNode<T> implements Pool.Poolable {

    public T next;
    public T prev;

    public void reset() {
        next = null;
        prev = null;
    }

    public T next() { return next; }
    public T prev() { return prev; }

    public void setNext(T next) { this.next = next; }
    public void setPrev(T prev) { this.prev = prev; }

}
