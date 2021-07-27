package zendo.games.grotto.utils;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;

public class VectorPool {

    public static Pool<Vector2> dim2 = Pools.get(Vector2.class, 100);
    public static Pool<Vector3> dim3 = Pools.get(Vector3.class, 100);

}
