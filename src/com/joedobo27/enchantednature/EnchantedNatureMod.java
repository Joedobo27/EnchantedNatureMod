package com.joedobo27.enchantednature;


import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * :ENCHANTED TILE CREATION:
 * new ActionEntry((short)388, "Enchant", "enchanting", Actions.EMPTY_INT_ARRAY)
 * TileTreeBehaviour.getBehavioursFor() and TileGrassBehaviour.getBehavioursFor() along with logic to verify performer
 * can enchant adds "Enchant" to the nature sub menu.
 * TileBehaviour.action() and action #388 calls Terraforming.enchantNature().
 *
 * :USAGE:
 * Creature.grazeNonCorrupt() This is where animal grazing can pack an enchanted grass tile.
 * 1 in 20 to check packing every grazing. Enchanted grass has further rolls for packing; 1:80 off-deed, 1:120 deed
 * with bad ration 1:240 for deed with good ration.
 * Zone.createTrack() Every time a creature moves this is called. For grass, lawn, reed, dirt and Mycelium tiles there
 * is a 2% chance when there are more then 20 tracks on a tile for it to pack.
 *
 * -------------------
 * Polling tiles and the possibility of adding harvesting of grass, flowers and other non-enchanted features to enchanted tiles.
 * TilePoller.pollNextTile() > TilePoller.checkEffects(). CheckEffects is the primary method for checking what should happen.
 */
public class EnchantedNatureMod implements WurmServerMod, Initable, Configurable, ServerStartedListener  {

    private static Logger logger;
    private static ClassPool classPool;

    private boolean grazeNeverPacks;
    private boolean noOverageStage;
    private boolean fasterTreeGrowth;

    @Override
    public void configure(Properties properties) {
        grazeNeverPacks = Boolean.parseBoolean(properties.getProperty("grazeNeverPacks", Boolean.toString(grazeNeverPacks)));
        noOverageStage = Boolean.parseBoolean(properties.getProperty("noOverageStage", Boolean.toString(noOverageStage)));
        fasterTreeGrowth = Boolean.parseBoolean(properties.getProperty("fasterTreeGrowth", Boolean.toString(fasterTreeGrowth)));
    }


    @Override
    public void init() {
        try {
            disablePackingInGrazeNonCorrupt();
            noOverageInCheckForTreeGrowth();
            fasterGrowthInCheckForTreeGrowth();
        } catch (NotFoundException | CannotCompileException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }

    }

    @Override
    public void onServerStarted() {
        JAssistClassData.voidClazz();
    }

    /**
     * was-
     *   final int chance = TilePoller.entryServer ? Server.rand.nextInt(20) : Server.rand.nextInt(225);
     *
     * becomes -
     *   Change the 255 arg to 127 if theTile.isEnchanted() is true. This should double the chance for an enchanted
     *   tree to age.
     *
     * Bytecode line 131.
     *
     * @throws NotFoundException JA related, forwarded.
     * @throws CannotCompileException JA related, forwarded.
     */
    private void fasterGrowthInCheckForTreeGrowth() throws NotFoundException, CannotCompileException {
        if (!fasterTreeGrowth)
            return;
        JAssistClassData tilePoller = JAssistClassData.getClazz("TilePoller");
        if (tilePoller == null) {
            tilePoller = new JAssistClassData("com.wurmonline.server.zones.TilePoller", classPool);
        }
        JAssistMethodData checkForTreeGrowth = new JAssistMethodData(tilePoller, "(IIIBB)V", "checkForTreeGrowth");
        checkForTreeGrowth.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("nextInt", methodCall.getMethodName()) && methodCall.indexOfBytecode() == 131) {
                    methodCall.replace("" +
                            "{" +
                                "$1 = theTile.isEnchanted() ? 127 : 255;" +
                                "$_ = $proceed($$);" +
                            "}");
                }
            }
        });
    }

    /**
     * was-
     *   final byte newType2 = convertToNewType(theTile, newData2);
     *
     * becomes-
     *   Change the value passed in for newData2 if the tree's age is 15. Recalculate tree age using age 14:
     *   newData2 = (byte)((age << 4) + partdata & 0xFF);
     *
     * Bytecode index 391 which goes in line number 1940.
     *
     * @throws NotFoundException JA related, forwarded.
     * @throws CannotCompileException JA related, forwarded.
     */
    private void noOverageInCheckForTreeGrowth() throws NotFoundException, CannotCompileException {
        if (!noOverageStage)
            return;
        JAssistClassData tilePoller = JAssistClassData.getClazz("TilePoller");
        if (tilePoller == null) {
            tilePoller = new JAssistClassData("com.wurmonline.server.zones.TilePoller", classPool);
        }
        JAssistMethodData checkForTreeGrowth = new JAssistMethodData(tilePoller, "(IIIBB)V", "checkForTreeGrowth");
        checkForTreeGrowth.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("convertToNewType", methodCall.getMethodName()) && methodCall.getLineNumber() == 1940) {
                    methodCall.replace("" +
                            "{" +
                                "$2 = age == 15 ? (byte)(((14 << 4) + partdata) & 0xFF) : newData2;" +
                                "$_ = $proceed($$);" +
                            "}");
                }
            }
        });
    }

    /**
     * was-
     *   type == Tiles.Tile.TILE_GRASS.id || type == Tiles.Tile.TILE_STEPPE.id ||
     *   (type == Tiles.Tile.TILE_ENCHANTED_GRASS.id && Server.rand.nextInt(enchGrassPackChance) == 0))
     *
     * becomes-
     *   if type == Tiles.Tile.TILE_ENCHANTED_GRASS.id alter the return value from TILE_ENCHANTED_GRASS.id so it's never true.
     *
     * Bytecode index 164&167 which goes with line number 6248 block.
     *
     * @throws NotFoundException JA related, forwarded.
     * @throws CannotCompileException JA related, forwarded.
     */
    private void disablePackingInGrazeNonCorrupt() throws NotFoundException, CannotCompileException {
        if (!grazeNeverPacks)
            return;
        JAssistClassData creature = new JAssistClassData("com.wurmonline.server.creatures.Creature", classPool);
        JAssistMethodData grazeNonCorrupt = new JAssistMethodData(creature,
                "(IBLcom/wurmonline/server/villages/Village;)Z", "grazeNonCorrupt");
        grazeNonCorrupt.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(FieldAccess fieldAccess) throws CannotCompileException {
                if (Objects.equals("id", fieldAccess.getFieldName()) && fieldAccess.indexOfBytecode() == 167) {
                    fieldAccess.replace("$_ = type == com.wurmonline.mesh.Tiles.Tile.TILE_ENCHANTED_GRASS.id ? 0 : type;");
                }
            }
        });
    }

    static {
        logger = Logger.getLogger(EnchantedNatureMod.class.getName());
        classPool = HookManager.getInstance().getClassPool();
    }
}
