package zendo.games.grotto.aseprite;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.PixmapPackerIO;
import com.badlogic.gdx.utils.Json;
import zendo.games.grotto.sprites.SpriteInfo;

import java.io.File;
import java.io.IOException;

public class AsepritePacker extends ApplicationAdapter {

    private static final String tag = AsepritePacker.class.getSimpleName();

    // ------------------------------------------------------------------------

    public static void main(String[] args) {
        new HeadlessApplication(new AsepritePacker(args), new HeadlessApplicationConfiguration());
    }

    // ------------------------------------------------------------------------

    private String[] args;

    public AsepritePacker(String[] args) {
        this.args = args;
    }

    @Override
    public void create() {
        String asepriteInputDir = null;
        String atlasOutputDir   = null;
        String spriteOutputDir  = null;
        String atlasFileName    = "sprites.atlas";

        // parse out pack params from args
        switch (args.length) {
            case 4: atlasFileName    = args[3];
            case 3: atlasOutputDir   = args[2];
            case 2: spriteOutputDir  = args[1];
            case 1: asepriteInputDir = args[0];
                break;
            default: {
                System.out.println(tag + " Usage: inputDir [spriteOutputDir] [packOutputDir] [packFileName]");
                System.exit(0);
            }
        }

        // convert output dir to absolute path
        // TODO: since we're using Gdx.files, this probably isn't necessary
        //  unless we want to allow output to any arbitrary dir outside the project
        if (atlasOutputDir == null) {
            File inputFile = new File(asepriteInputDir);
            atlasOutputDir = new File(inputFile.getParentFile(), inputFile.getName() + "-packed").getAbsolutePath();
        } else {
            atlasOutputDir = new File(atlasOutputDir).getAbsolutePath();
        }

        if (spriteOutputDir == null) {
            File inputFile = new File(asepriteInputDir);
            spriteOutputDir = new File(inputFile.getParentFile(), inputFile.getName() + "-sprites").getAbsolutePath();
        } else {
            spriteOutputDir = new File(spriteOutputDir).getAbsolutePath();
        }

        Gdx.app.log(tag, "Params:"
                + "\n\tinputDir = " + asepriteInputDir
                + "\n\tspriteOutputDir = " + spriteOutputDir
                + "\n\tatlasOutputDir = " + atlasOutputDir
                + "\n\tatlasFileName = " + atlasFileName
        );

        try {
            process(asepriteInputDir, spriteOutputDir, atlasOutputDir, atlasFileName);
        } catch (IOException e) {
            Gdx.app.error(tag, "Failed to pack atlas from aseprite files", e);
        }

        Gdx.app.exit();
    }

    private void process(String inputDir, String spriteOutputDir, String atlasOutputDir, String atlasFileName) throws IOException {
        // configure a pixmap packer
        // TODO: maybe optionally pass some of these as args?
        int pageWidth = 1024;
        int pageHeight = 1024;
        Pixmap.Format pageFormat = Pixmap.Format.RGBA8888;
        int padding = 0;
        boolean duplicateBorder = false;
        boolean stripWhitespaceX = false;
        boolean stripWhitespaceY = false;
        PixmapPacker.PackStrategy packStrategy = new PixmapPacker.GuillotineStrategy();
        PixmapPacker packer = new PixmapPacker(
                pageWidth, pageHeight, pageFormat, padding,
                duplicateBorder, stripWhitespaceX, stripWhitespaceY,
                packStrategy);

        // load aseprite files, pack animation frame pixmaps into atlas, write out sprite info
        Json json = new Json();
        for (FileHandle aseFile : Gdx.files.internal(inputDir).list(".ase")) {
            SpriteInfo spriteInfo = Aseprite.loadAndPack(packer, inputDir + aseFile.name());
            json.toJson(spriteInfo, SpriteInfo.class,
                    Gdx.files.getFileHandle(spriteOutputDir + "/" + spriteInfo.name + ".json", Files.FileType.Absolute));
        }

        // write out texture atlas files to system
        FileHandle outFileHandle = Gdx.files.getFileHandle(atlasOutputDir + "/" + atlasFileName, Files.FileType.Absolute);
        PixmapPackerIO packerIO = new PixmapPackerIO();
        PixmapPackerIO.SaveParameters saveParams = new PixmapPackerIO.SaveParameters();
        saveParams.useIndexes = true; // note - defaults are fine, except we do want to use indexes
        packerIO.save(outFileHandle, packer, saveParams);

        Gdx.app.log(tag, "Processing complete");
    }

}
