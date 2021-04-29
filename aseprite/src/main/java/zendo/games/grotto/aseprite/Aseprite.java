package zendo.games.grotto.aseprite;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectMap;
import zendo.games.grotto.sprites.SpriteInfo;
import zendo.games.grotto.utils.Point;
import zendo.games.grotto.utils.RectI;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * A simple Aseprite file parser
 * @link https://github.com/aseprite/aseprite/blob/master/docs/ase-file-specs.md
 *
 * Based on NoelFB's Blah framework
 * @link https://github.com/noelfb/blah
 */
public class Aseprite {

    private static final String tag = Aseprite.class.getSimpleName();

    // ----------------------------------------------------
    // enumerations
    // ----------------------------------------------------

    enum Modes {
          indexed   (1)
        , grayscale (2)
        , rgba      (4)
        ;
        public final int value;
        Modes(int value) { this.value = value; }

        public static Modes fromValue(int value) {
            switch (value) {
                case 1: return indexed;
                case 2: return grayscale;
                case 4: return rgba;
                default: throw new GdxRuntimeException("Invalid Aseprite.Modes value: " + value);
            }
        }
    }

    enum Chunks {
          OldPaletteA  (0x0004) // ignore if Palette (0x2019) is found
        , OldPaletteB  (0x0011) // ignore if Palette (0x2019) is found
        , Layer        (0x2004)
        , Cel          (0x2005)
        , CelExtra     (0x2006)
        , ColorProfile (0x2007)
        , Mask         (0x2016) // deprecated
        , Path         (0x2017) // never used
        , FrameTags    (0x2018)
        , Palette      (0x2019)
        , UserData     (0x2020)
        , Slice        (0x2022)
        ;
        public final int value;
        Chunks(int value) { this.value = value; }

        public static Chunks fromValue(int value) {
            switch (value) {
                case 0x0004: return OldPaletteA;
                case 0x0011: return OldPaletteB;
                case 0x2004: return Layer;
                case 0x2005: return Cel;
                case 0x2006: return CelExtra;
                case 0x2007: return ColorProfile;
                case 0x2016: return Mask;
                case 0x2017: return Path;
                case 0x2018: return FrameTags;
                case 0x2019: return Palette;
                case 0x2020: return UserData;
                case 0x2022: return Slice;
                default: throw new GdxRuntimeException("Invalid Aseprite.Chunks value: " + value);
            }
        }
    }

    enum LoopDirections {
          Forward(0)
        , Reverse(1)
        , PingPong(2)
        ;
        public final int value;
        LoopDirections(int value) { this.value = value; }

        public static LoopDirections fromValue(int value) {
            switch (value) {
                case 0: return Forward;
                case 1: return Reverse;
                case 2: return PingPong;
                default: throw new GdxRuntimeException("Invalid Aseprite.LoopDirections value: " + value);
            }
        }
    }

    enum LayerTypes {
          Normal(0)
        , Group(1)
        ;
        public final int value;
        LayerTypes(int value) { this.value = value; }

        // *sad trombone* why you gotta be this way java
        public static LayerTypes fromValue(int value) {
            switch (value) {
                case 0: return Normal;
                case 1: return Group;
                default: throw new GdxRuntimeException("Invalid Aseprite.LayerTypes value: " + value);
            }
        }
    }

    // ----------------------------------------------------
    // bitmask constants
    // ----------------------------------------------------

    static final int layer_flag_visible          = 1 << 0;
    static final int layer_flag_editable         = 1 << 1;
    static final int layer_flag_lockmovement     = 1 << 2;
    static final int layer_flag_backgroun        = 1 << 3;
    static final int layer_flag_preferlinkedcels = 1 << 4;
    static final int layer_flag_collapsed        = 1 << 5;
    static final int layer_flag_reference        = 1 << 6;


    // ----------------------------------------------------
    // structs
    // ----------------------------------------------------

    public static class UserData {
        public String text = null;
        public Color color = null;
    }

    public static class Cel {
        public int layer_index = 0;
        public int linked_frame_index = 0;
        public int x = 0;
        public int y = 0;
        public byte alpha = 0;
        public Pixmap image = null;
        public UserData userdata = null;
    }

    public static class Frame {
        public int duration = 0;
        public Pixmap image = null;
        public List<Cel> cels = null;
    }

    public static class Layer {
        public int flags = 0;
        public LayerTypes type = LayerTypes.Normal;
        public String name = "";
        public int child_level = 0;
        public int blendmode = 0;
        public byte alpha = 0;
        public boolean visible = true;
        public UserData userdata = null;
    }

    public static class Tag {
        public String name = "";
        public LoopDirections loops = LoopDirections.Forward;
        public int from = 0;
        public int to = 0;
        public Color color = Color.WHITE.cpy();
        public UserData userdata = null;
    }

    public static class Slice {
        public int frame = 0;
        public String name = "";
        public Point origin = Point.at(0, 0);
        public int width = 0;
        public int height = 0;
        public boolean has_pivot = false;
        public Point pivot = Point.at(0, 0);
        public UserData userdata = null;
    }

    // ----------------------------------------------------
    // fields
    // ----------------------------------------------------

    public Modes mode = Modes.rgba;
    public int width  = 0;
    public int height = 0;

    public ArrayList<Layer> layers  = new ArrayList<>();
    public ArrayList<Frame> frames  = new ArrayList<>();
    public ArrayList<Tag>   tags    = new ArrayList<>();
    public ArrayList<Slice> slices  = new ArrayList<>();
    public ArrayList<Color> palette = new ArrayList<>();

    private UserData lastUserdata = null;

    // ----------------------------------------------------
    // constructors
    // ----------------------------------------------------

    public Aseprite(String path) {
        this(Gdx.files.internal(path));
    }

    public Aseprite(FileHandle file) {
        parse(file);
    }

    // ----------------------------------------------------
    // loading helper
    // ----------------------------------------------------

    /**
     * Load the Aseprite file specified by 'path', packing animation frames
     * with the specified 'packer' and disposing of loaded Pixmap data from
     * the Aseprite files after it is packed
     *
     * @param packer a configured PixmapPacker used to pack animation frame data
     * @param path the path of the Aseprite file to load
     *
     * @return a SpriteInfo object populated with details of the loaded Aseprite file
     *         and references for how to find the TextureRegions packed by the PixmapPacker
     */
    public static SpriteInfo loadAndPack(PixmapPacker packer, String path) {
        SpriteInfo info = new SpriteInfo();
        {
            Aseprite aseprite = new Aseprite(path);
            info.path = path;
            info.name = path.subSequence(path.lastIndexOf('/') + 1, path.indexOf(".ase")).toString();
            info.slice_pivot = Point.zero();
            info.anim_frame_infos = new ObjectMap<>();

            // set slice pivot point if a slice with a pivot has been defined
            if (aseprite.slices.size() > 0 && aseprite.slices.get(0).has_pivot) {
                Slice slice = aseprite.slices.get(0);
                // the slice pivot should be y-up at this point to match in-game sprite origin with aseprite y-down pivot position
                info.slice_pivot.set(slice.pivot.x, slice.pivot.y);
            }

            // build animation info for each tag
            for (Tag anim_tag : aseprite.tags) {
                int num_frames = anim_tag.to - anim_tag.from + 1;

                // build frame infos for each frame of this animation
                info.anim_frame_infos.put(anim_tag.name, new Array<>());
                for (int i = 0; i < num_frames; i++) {
                    int frame_index = anim_tag.from + i;

                    // collect frame information from the aseprite file
                    // note:
                    //  the string used for atlas.findRegion must _not_ include the frame index
                    //  while the string used to pack a region into the atlas _must_ include the frame index
                    //  ... assuming that AsepritePacker is using a PixmapPackerIO.SaveParameters with useIndex = true
                    Frame frame = aseprite.frames.get(frame_index);
                    String frame_region_name = info.name + "-" + anim_tag.name;
                    String frame_region_name_w_index = frame_region_name + "_" + i;
                    float frame_duration = frame.duration;

                    // pack the frame image into the texture atlas
                    packer.pack(frame_region_name_w_index, frame.image);

                    // save the info needed to build the sprite's animation for this tag/frame
                    Array<SpriteInfo.AnimFrameInfo> anim_frame_infos = info.anim_frame_infos.get(anim_tag.name);
                    SpriteInfo.AnimFrameInfo anim_frame_info = new SpriteInfo.AnimFrameInfo();
                    anim_frame_info.region_name = frame_region_name;
                    anim_frame_info.region_index = i;
                    anim_frame_info.duration = frame_duration;
                    anim_frame_info.hitbox = extract_hitbox_data(aseprite, info, frame);
                    anim_frame_infos.add(anim_frame_info);
                }
            }

            // dispose Aseprite Pixmap images since they are now packed into the texture atlas
            for (Frame frame : aseprite.frames) {
                for (Cel cel : frame.cels) {
                    cel.image.dispose();
                }
                frame.image.dispose();
            }
        }
        return info;
    }

    /**
     * If there is a layer named "hitbox", and a cel in that layer in the specified frame,
     * try to extract pixel data from that cel and convert it into rectangular hitbox offsets
     *
     * @param info the SpriteInfo containing sprite data to try to extract hitbox data from
     * @param frame the Aseprite.Frame to try to extract hitbox data for
     *
     * @return extents and offsets from the sprite's pivot point that define a hitbox region for the specified frame,
     *         or null if any of the following conditions are true
     *         - no hitbox layer is found in the aseprite
     *         - no cel exists in the hitbox layer
     *         - all pixels in the hitbox cel for this frame are transparent
     */
    private static RectI extract_hitbox_data(Aseprite aseprite, SpriteInfo info, Frame frame) {
        // check whether a hitbox layer exists
        int hitbox_layer_index = -1;
        for (int layer_index = 0; layer_index < aseprite.layers.size(); layer_index++) {
            Layer layer = aseprite.layers.get(layer_index);
            if ("hitbox".equals(layer.name)) {
                hitbox_layer_index = layer_index;
                break;
            }
        }

        // no hitbox layer found
        if (hitbox_layer_index == -1) {
            return null;
        }

        // extract the slice and pivot point (if any)
        int pivot_x = 0;
        int pivot_y = 0;
        int slice_h = 0;
        Slice slice = aseprite.slices.get(0);
        if (slice != null) {
            // flip slice pivot point to be y-up to match in-game reference with aseprite pivot point
            slice_h = slice.height;
            pivot_x = slice.pivot.x;
            pivot_y = slice_h - slice.pivot.y;
        }

        // note:
        //  a Cel is defined by non-transparent pixels in a frame
        //  so cel (x,y) are offsets from frame (0,0), except (0,0) is top left in both cases
        //  but we're flipping y to make the slice pivot point look correct in game,
        //  so some extra work needs to be done to get the correct cel (x,y) for extents,
        //  then those extent values need to be shifted to take into account the pivot point
        for (Cel cel : frame.cels) {
            if (cel.layer_index == hitbox_layer_index) {
                // check whether there are any non-transparent pixels in this cel
                if (cel.image.getWidth() == 0 || cel.image.getHeight() == 0) {
                    return null;
                }

                // flip cel to y-up, relative to slice bounds (which should just match image bounds)
                RectI hitbox_extents = RectI.at(
                        cel.x, slice_h - cel.y - cel.image.getHeight(),
                        cel.image.getWidth(), cel.image.getHeight()
                );

                // calculate offsets from extents and pivot position
                RectI hitbox_offsets = RectI.at(
                        hitbox_extents.x - pivot_x,
                        hitbox_extents.y - pivot_y,
                        hitbox_extents.w,
                        hitbox_extents.h
                );

                // return the calculated offsets for the specified animation frame
                return RectI.at(hitbox_offsets);
            }
        }

        // no cell found in hitbox layer for this frame
        return null;
    }

    // ----------------------------------------------------
    // implementation
    // ----------------------------------------------------

    private void parse(FileHandle file) {
        System.out.println("[" + tag + "] Loading file: " + file.path());
        if (!file.exists()) {
            throw new GdxRuntimeException("Aseprite file does not exist: " + file.path());
        }

        // create byte buffer from file contents and set endianness for .ase files
        byte[] bytes = file.readBytes();
        ByteBuffer stream = ByteBuffer.wrap(bytes);
        stream.order(ByteOrder.LITTLE_ENDIAN);

        int frame_count = 0;

        // header
        {
            // extract filesize, but it's unused so don't store it
            stream.getInt();

            // extract and validate magic number
            short magic = stream.getShort();
            if (magic != (short)0xA5E0) {
                throw new GdxRuntimeException("File is not a valid Aseprite file (bad header magic): " + file.path());
            }

            // extract main data
            frame_count = stream.getShort();
            width = stream.getShort();
            height = stream.getShort();
            mode = Modes.fromValue(stream.getShort() / 8);

            // don't care about other info, extract and drop on the floor
            stream.getInt();   // flags
            stream.getShort(); // speed (deprecated)
            stream.getInt();   // should be 0
            stream.getInt();   // should be 0
            stream.get();      // palette entry
            stream.position(stream.position() + 3); // skip reserved bytes
            stream.getShort(); // number of colors (0 means 256 for old sprites)
            stream.get();      // pixel width
            stream.get();      // pixel height
            stream.position(stream.position() + 92); // skip reserved bytes
        }

        // instantiate frames to be parsed
        for (int i = 0; i < frame_count; i++) {
            frames.add(new Frame());
        }

        // parse frames
        for (int i = 0; i < frame_count; i++) {
            int frameStart = stream.position();
            int frameSize = stream.getInt();
            int frameEnd = frameStart + frameSize;
            int chunks = 0;

            // frame header
            {
                // extract and validate magic number
                short magic = stream.getShort();
                if (magic != (short)0xF1FA) {
                    throw new GdxRuntimeException("File is not a valid Aseprite file (bad chunk magic): " + file.path());
                }

                // extract chunk counts (both old and new) and frame duration
                short old_chunk_count  = stream.getShort();
                frames.get(i).duration = stream.getShort();
                stream.position(stream.position() + 2); // skip reserved bytes
                int new_chunk_count    = stream.getInt();

                // set number of chunks, using the appropriate chunk count for the file
                if (old_chunk_count == (short)0xFFFF) {
                    chunks = new_chunk_count;
                } else {
                    chunks = old_chunk_count;
                }
            }

            // create the frame image
            frames.get(i).image = new Pixmap(width, height, Pixmap.Format.RGBA8888);

            // frame chunks
            for (int j = 0; j < chunks; j++) {
                int chunkStart = stream.position();
                int chunkEnd = chunkStart + stream.getInt();
                Chunks chunkType = Chunks.fromValue(stream.getShort());

                switch (chunkType) {
                    case Layer:     parse_layer     (stream, i);           break;
                    case Cel:       parse_cel       (stream, i, chunkEnd); break;
                    case Palette:   parse_palette   (stream, i);           break;
                    case UserData:  parse_user_data (stream, i);           break;
                    case FrameTags: parse_tag       (stream, i);           break;
                    case Slice:     parse_slice     (stream, i);           break;
                    default: System.out.println("[" + tag + "] Ignoring chunk: " + chunkType.name());
                }

                stream.position(chunkEnd);
            }

            // move to end of frame
            stream.position(frameEnd);
        }

        System.out.println("[" + tag + "] File loaded: " + file.path());
    }

    private void parse_layer(ByteBuffer stream, int frame) {
        Layer layer = new Layer();
        {
            layer.flags = stream.getShort();
            layer.visible     = ((layer.flags & layer_flag_visible) == layer_flag_visible);
            layer.type        = LayerTypes.fromValue(stream.getShort());
            layer.child_level = stream.getShort();
            stream.getShort(); // skip width
            stream.getShort(); // skip height
            layer.blendmode   = stream.getShort();
            layer.alpha       = stream.get();
            stream.position(stream.position() + 3); // skip reserved bytes

            short nameLength = stream.getShort();
            byte[] nameBytes = new byte[nameLength];
            stream.get(nameBytes, 0, nameLength);
            layer.name = new String(nameBytes);

            layer.userdata = new UserData();
            layer.userdata.color = Color.WHITE.cpy();
            layer.userdata.text = "";
            lastUserdata = layer.userdata;
        }
        layers.add(layer);
    }

    private void parse_cel(ByteBuffer stream, int frameIndex, int maxPosition) {
        Frame frame = frames.get(frameIndex);
        if (frame.cels == null) {
            frame.cels = new ArrayList<>();
        }

        Cel cel = new Cel();
        {
            cel.layer_index = stream.getShort();
            cel.x = stream.getShort();
            cel.y = stream.getShort();
            cel.alpha = stream.get();
            cel.linked_frame_index = -1;

            short cel_type = stream.getShort();
            stream.position(stream.position() + 7); // skip reserved bytes

            // RAW or DEFLATE
            if (cel_type == 0 || cel_type == 2) {
                short width  = stream.getShort();
                short height = stream.getShort();
                int num_image_bytes = width * height * mode.value;

                // create the backing pixmap
                cel.image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
                ByteBuffer imageBytes = ByteBuffer.allocate(num_image_bytes);

                // load pixels in rgba format
                // RAW
                if (cel_type == 0) {
                    stream.get(imageBytes.array(), 0, num_image_bytes);
                }
                // DEFLATE
                else {
                    // try to decode the pixel bytes
                    try {
                        // note - in noel's parser he clamps this value at INT32_MAX
                        //        not sure how the value could get bigger since its the diff of 2 ints
                        int size = maxPosition - stream.position();
                        byte[] buffer = new byte[size];
                        stream.get(buffer, 0, size);

                        // sizeof Color in bytes = 4
                        int output_length = width * height * 4;

                        Inflater inflater = new Inflater();
                        inflater.setInput(buffer, 0, size);
                        inflater.inflate(imageBytes.array(), 0, output_length);
                    } catch (DataFormatException e) {
                        throw new GdxRuntimeException("File is not a valid Aseprite file (unable to inflate cel pixel data for frame): " + frameIndex);
                    }
                }

                // todo - review these conversions, they're probably not right

                // convert rgba loaded pixels to another format if mode is not rgba
                // note - we work in-place to save having to store stuff in a buffer
                if (mode == Modes.grayscale) {
                    System.out.println("[" + tag + "] converting cel pixels to grayscale not yet implemented");
//                    var src = cel.image.getPixels().array();
//                    var dst = cel.image.getPixels().array();
//
//                    for (int d = width * height - 1, s = (width * height - 1) * 2; d >= 0; d--, s -= 2) {
//                        dst[d] = new Color(src[s], src[s], src[s], src[s + 1]);
//                    }
                }
                else if (mode == Modes.indexed) {
                    System.out.println("[" + tag + "] possibly broken: converting cel pixels to indexed colors....");
                    ByteBuffer src = imageBytes;
                    ByteBuffer dst = imageBytes;
                    for (int i = src.array().length - 1; i >= 0; i -= 4) {
                        // TODO: double check byte ordering, this is a bit oof
                        // convert source bytes into integer palette index
                        byte[] srcBytes = new byte[] {src.get(i), src.get(i-1), src.get(i-2), src.get(i-3)};
                        int palette_index = ByteBuffer.wrap(srcBytes).getInt();

                        // retrieve the indexed color from the previously loaded palette
                        Color indexed_color = palette.get(palette_index);

                        // convert indexed color to int bytes and write back to dst
                        byte[] result = ByteBuffer.allocate(4).putInt(indexed_color.toIntBits()).array();
                        dst.put(i - 0, result[0]);
                        dst.put(i - 1, result[1]);
                        dst.put(i - 2, result[2]);
                        dst.put(i - 3, result[3]);
                    }
                }

                // update the pixels in the cel's pixmap
                cel.image.getPixels().put(imageBytes);
            }
            // REFERENCE (this cel directly references a previous cel)
            else if (cel_type == 1) {
                cel.linked_frame_index = stream.getShort();
            }

            // draw to frame if visible
            // note:
            //  render_cel doesn't properly composite multiple cels per frame (yet)
            //  also, hitbox layers should not get rendered into the frame
            if ((layers.get(cel.layer_index).flags & layer_flag_visible) != 0
             && (!layers.get(cel.layer_index).name.equals("hitbox"))) {
                render_cel(cel, frame);
            }

            // update userdata
            cel.userdata = new UserData();
            cel.userdata.color = Color.WHITE.cpy();
            cel.userdata.text = "";
            lastUserdata = cel.userdata;
        }
        frame.cels.add(cel);
    }

    private void parse_palette(ByteBuffer stream, int frame) {
        stream.getInt(); // size
        int start = stream.getInt();
        int end   = stream.getInt();
        stream.position(stream.position() + 8); // skip reserved bytes

        int newSize = palette.size() + (end - start) + 1;
        palette.ensureCapacity(newSize);

        for (int p = 0, len = (end - start) + 1; p < len; p++) {
            short hasName = stream.getShort();

            // colors are stored in big endian order
            // so temporarily reverse byte order to read the color out
            stream.order(ByteOrder.BIG_ENDIAN);
            palette.add(start + p, new Color(stream.getInt()));
            stream.order(ByteOrder.LITTLE_ENDIAN);

            if ((hasName & 0xF000) != 0) {
                len = stream.getShort();
                stream.position(stream.position() + len);
            }
        }
    }

    private void parse_user_data(ByteBuffer stream, int frame) {
        if (lastUserdata != null) {
            int flags = stream.getInt();

            // has text
            if ((flags & (1 << 0)) != 0) {
                short textLength = stream.getShort();
                byte[] textBytes = new byte[textLength];
                stream.get(textBytes, 0, textLength);
                lastUserdata.text = new String(textBytes);
            }

            // has color
            if ((flags & (1 << 1)) != 0) {
                // colors are stored in big endian order
                // so temporarily reverse byte order to read the color out
                stream.order(ByteOrder.BIG_ENDIAN);
                lastUserdata.color = new Color(stream.getInt());
                stream.order(ByteOrder.LITTLE_ENDIAN);
            }
        }
    }

    private void parse_tag(ByteBuffer stream, int frame) {
        short num_tags = stream.getShort();
        stream.position(stream.position() + 8); // skip reserved bytes

        for (int i = 0; i < num_tags; i++) {
            Tag tag = new Tag();
            {
                tag.from = stream.getShort();
                tag.to   = stream.getShort();
                tag.loops = LoopDirections.fromValue(stream.get());
                stream.position(stream.position() + 8); // skip reserved bytes

                // note - this might not be correct
                //        the spec shows byte[3] (rgb tag color) then byte[1] (extra zero byte)
                //        not sure if the extra zero byte gets picked up as the alpha byte
                // colors are stored in big endian order
                // so temporarily reverse byte order to read the color out
                stream.order(ByteOrder.BIG_ENDIAN);
                tag.color = new Color(stream.getInt());
                tag.color.a = 1f;
                stream.order(ByteOrder.LITTLE_ENDIAN);

                short nameLength = stream.getShort();
                byte[] nameBytes = new byte[nameLength];
                stream.get(nameBytes, 0, nameLength);
                tag.name = new String(nameBytes);
            }
            tags.add(tag);
        }
    }

    private void parse_slice(ByteBuffer stream, int frame) {
        int num_slices = stream.getInt();
        int flags      = stream.getInt();
        stream.getInt(); // skip reserved bytes

        short nameLength = stream.getShort();
        byte[] nameBytes = new byte[nameLength];
        stream.get(nameBytes, 0, nameLength);
        String name = new String(nameBytes);

        for (int i = 0; i < num_slices; i++) {
            Slice slice = new Slice();
            {
                slice.name     = name;
                slice.frame    = stream.getInt();
                slice.origin.x = stream.getInt();
                slice.origin.y = stream.getInt();
                slice.width    = stream.getInt();
                slice.height   = stream.getInt();

                // 9 slice (ignored for now)
                if ((flags & (1 << 0)) != 0) {
                    stream.getInt();
                    stream.getInt();
                    stream.getInt();
                    stream.getInt();
                }

                // pivot point
                slice.has_pivot = false;
                if ((flags & (1 << 1)) != 0) {
                    slice.has_pivot = true;
                    slice.pivot.x = stream.getInt();
                    slice.pivot.y = stream.getInt();
                    // pivot points are defined in a y-down coordinate system
                    // convert them to y-up here so that they match object coords in game
                    slice.pivot.y = slice.height - slice.pivot.y;
                }

                slice.userdata = new UserData();
                slice.userdata.color = Color.WHITE.cpy();
                slice.userdata.text = "";
                lastUserdata = slice.userdata;
            }
            slices.add(slice);
        }
    }

    // note - since a frame could contain multiple cells
    //        this method should composite the specified cell
    //        into the existing frame image rather than
    //        just overwriting the existing frame image
    private void render_cel(Cel cel, Frame frame) {
        frame.image.drawPixmap(cel.image, cel.x, cel.y);
    }

}
