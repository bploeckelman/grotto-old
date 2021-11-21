package zendo.games.grotto.components;

import com.badlogic.gdx.math.Vector2;
import zendo.games.grotto.ecs.Component;
import zendo.games.grotto.utils.Calc;
import zendo.games.grotto.utils.Point;

public class Mover extends Component {

    public interface OnHit {
        void hit(Mover mover);
    }

    public interface OnSquish {
        void squish(Mover mover);
    }

    public Vector2 speed;
    public Collider collider;
    public OnHit onHitX;
    public OnHit onHitY;
    public OnSquish onSquishX;
    public OnSquish onSquishY;
    public float gravity;
    public float friction;

    private Vector2 remainder;

    public Mover() {
        super();
        speed = new Vector2();
        remainder = new Vector2();
    }

    @Override
    public void reset() {
        super.reset();
        speed = null;
        collider = null;
        onHitX = null;
        onHitY = null;
        onSquishX = null;
        onSquishY = null;
        gravity = 0;
        friction = 0;
        remainder = null;
    }

    public boolean isRiding(Solid solid) {
        var riding = false;
        if (collider != null) {
            var isColliding  = collider.overlaps(solid.collider, Point.zero());
            var isAboveSolid = collider.overlaps(solid.collider, Point.at(0, -1));
            riding = !isColliding && isAboveSolid;
        }
        return riding;
    }

    @Override
    public void update(float dt) {
        // apply friction if appropriate
        if (friction > 0 && onGround()) {
            speed.x = Calc.approach(speed.x, 0, friction * dt);
        }

        // apply gravity if appropriate
        var isNotCollidingWithGround = (collider == null) || (!collider.check(Collider.Mask.solid, Point.at(0, -1)));
        if (gravity != 0 && isNotCollidingWithGround) {
            speed.y += gravity * dt;
        }

        // get the amount we should move, including remainder from previous frame
        float totalMoveX = remainder.x + speed.x * dt;
        float totalMoveY = remainder.y + speed.y * dt;

        // round to integer values to only move a pixel at a time
        int intMoveX = (int) totalMoveX;
        int intMoveY = (int) totalMoveY;

        // store the fractional remainder
        remainder.x = totalMoveX - intMoveX;
        remainder.y = totalMoveY - intMoveY;

        // move by the integer values
        moveX(intMoveX, onSquishX);
        moveY(intMoveY, onSquishY);
    }

    public boolean moveX(int amount, OnSquish onSquish) {
        if (collider == null) {
            entity.position.x += amount;
        } else {
            var sign = Calc.sign(amount);

            while (amount != 0) {
                if (collider.check(Collider.Mask.solid, Point.at(sign, 0))) {
                    if (onHitX != null) {
                        onHitX.hit(this);
                    } else {
                        stopX();
                    }
                    if (onSquish != null) {
                        onSquish.squish(this);
                    }
                    return true;
                }

                amount -= sign;
                entity.position.x += sign;
            }
        }

        return false;
    }

    public boolean moveY(int amount, OnSquish onSquish) {
        if (collider == null) {
            entity.position.y += amount;
        } else {
            var sign = Calc.sign(amount);

            while (amount != 0) {
                var isSolid = collider.check(Collider.Mask.solid, Point.at(0, sign));
                var isJumpthru = collider.check(Collider.Mask.jumpthru, Point.at(0, sign));
                var isInsideJumpthru = collider.check(Collider.Mask.jumpthru);
                var isMovingDown = sign < 0;
                if (isSolid || (isJumpthru && !isInsideJumpthru && isMovingDown)) {
                    if (onHitY != null) {
                        onHitY.hit(this);
                    } else {
                        stopY();
                    }
                    if (onSquish != null) {
                        onSquish.squish(this);
                    }
                    return true;
                }

                amount -= sign;
                entity.position.y += sign;
            }
        }

        return false;
    }

    public void stop() {
        stopX();
        stopY();
    }

    public void stopX() {
        speed.x = 0;
        remainder.x = 0;
    }

    public void stopY() {
        speed.y = 0;
        remainder.y = 0;
    }

    public boolean onGround() {
        return onGround(-1);
    }

    public boolean onGround(int dist) {
        if (collider == null) {
            return false;
        }

        var hitSolid = collider.check(Collider.Mask.solid, Point.at(0, dist));
        var isJumpthru = collider.check(Collider.Mask.jumpthru, Point.at(0, dist));
        var isInsideJumpthru = collider.check(Collider.Mask.jumpthru);
        var isMovingDown = dist < 0;
        var hitJumpthru = isJumpthru && !isInsideJumpthru && isMovingDown;

        return hitSolid || hitJumpthru;
    }

    public boolean onJumpthru() {
        return onJumpthru(-1);
    }

    public boolean onJumpthru(int dist) {
        if (collider == null) {
            return false;
        }

        var isJumpthru = collider.check(Collider.Mask.jumpthru, Point.at(0, dist));
        var isInsideJumpthru = collider.check(Collider.Mask.jumpthru);
        var isMovingDown = dist < 0;
        var onJumpthru = isJumpthru && !isInsideJumpthru && isMovingDown;

        return onJumpthru;
    }

}
