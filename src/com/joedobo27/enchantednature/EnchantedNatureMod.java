package com.joedobo27.enchantednature;



import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.items.*;
import com.wurmonline.server.skills.SkillList;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.Bytecode;
import javassist.bytecode.Opcode;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.classhooks.CodeReplacer;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;
import org.gotti.wurmunlimited.modsupport.IdFactory;
import org.gotti.wurmunlimited.modsupport.IdType;
import org.gotti.wurmunlimited.modsupport.ItemTemplateBuilder;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.joedobo27.enchantednature.BytecodeTools.addConstantPoolReference;
import static com.joedobo27.enchantednature.BytecodeTools.findConstantPoolReference;

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

public class EnchantedNatureMod implements WurmServerMod, Initable, Configurable, ServerStartedListener, ItemTemplatesCreatedListener {

    private static Logger logger;
    private static ClassPool classPool;

    private boolean grazeNeverPacks = false;
    private boolean noOverageStage = false;
    private boolean fasterTreeGrowth = false;
    private int growthAccelerator = 1;
    private boolean pickSprouts = false;
    private boolean enchantWithAlchemy = false;
    private boolean cutGrass = false;

    private static int treeEssenceTemplateId;
    private static int bushEssenceTemplateId;
    private static int grassEssenceTemplateId;

    @Override
    public void configure(Properties properties) {
        grazeNeverPacks = Boolean.parseBoolean(properties.getProperty("grazeNeverPacks", Boolean.toString(grazeNeverPacks)));
        noOverageStage = Boolean.parseBoolean(properties.getProperty("noOverageStage", Boolean.toString(noOverageStage)));
        fasterTreeGrowth = Boolean.parseBoolean(properties.getProperty("fasterTreeGrowth", Boolean.toString(fasterTreeGrowth)));
        growthAccelerator = Integer.parseInt(properties.getProperty("growthAccelerator", Integer.toString(growthAccelerator)));
        pickSprouts = Boolean.parseBoolean(properties.getProperty("pickSprouts", Boolean.toString(pickSprouts)));
        enchantWithAlchemy = Boolean.parseBoolean(properties.getProperty("enchantWithAlchemy", Boolean.toString(enchantWithAlchemy)));
        cutGrass = Boolean.parseBoolean(properties.getProperty("cutGrass", Boolean.toString(cutGrass)));
    }


    @Override
    public void init() {
        try {
            ModActions.init();

            boolean isGrazeNeverPacks = disablePackingInGrazeNonCorrupt();
            boolean isNoOverageStage = noOverageInCheckForTreeGrowth();
            boolean isFasterTreeGrowth = fasterGrowthInCheckForTreeGrowth();
            boolean isPickSprouts1 = pickSproutInGetNatureActions();
            boolean isPickSprouts2 = pickSproutInAction();
            boolean isPickSprouts3 = pickSproutInPickSprout();
            boolean isCutGrass1 = grassGrowthOnEnchantedGrass();
            boolean isCutGrass2 = cutGrassInGetNatureActions();

            logger.log(Level.INFO, String.format("grazeNeverPacks %b, noOverageStage %b, fasterTreeGrowth %b", isGrazeNeverPacks,
                    isNoOverageStage, isFasterTreeGrowth));
            logger.log(Level.INFO, String.format("isPickSprouts %b, isPickSprouts2 %b, isPickSprouts3 %b", isPickSprouts1,
                    isPickSprouts2, isPickSprouts3));
            logger.log(Level.INFO, String.format("isCutGrass1 %b, isCutGrass2 %b", isCutGrass1, isCutGrass2));
        } catch (NotFoundException | CannotCompileException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }

    }

    @Override
    public void onServerStarted() {
        if (enchantWithAlchemy) {
            ModActions.registerAction(new EnchantTileAction());
            ModActions.registerAction(new DisenchantTileAction());
            addCreationForNatureEssences();
            makeEnchantedGrassInTile();
        }
        if (cutGrass) {
            cutGrassInTile();
        }
        JAssistClassData.voidClazz();
    }

    @Override
    public void onItemTemplatesCreated() {
        if (enchantWithAlchemy) {
            ItemTemplateBuilder treeEssenceTemplate = new ItemTemplateBuilder("jdbTreeEssenceTemplate");
            treeEssenceTemplateId = IdFactory.getIdFor("jdbTreeEssenceTemplate", IdType.ITEMTEMPLATE);
            treeEssenceTemplate.name("Tree essence", "Tree essence", "A mystical miniature tree. It's used to enchant or disenchant a tree tile.");
            treeEssenceTemplate.size(3);
            treeEssenceTemplate.itemTypes(new short[]{ItemTypes.ITEM_TYPE_MAGIC, ItemTypes.ITEM_TYPE_BULK});
            treeEssenceTemplate.imageNumber((short) 484); // using sprout image number.
            treeEssenceTemplate.behaviourType((short) 1); // item behaviour
            treeEssenceTemplate.combatDamage(0);
            treeEssenceTemplate.decayTime(86400L); // sprout decay time.
            treeEssenceTemplate.dimensions(10, 10, 10);
            treeEssenceTemplate.primarySkill(-10);
            treeEssenceTemplate.modelName("model.resource.sprout.");
            treeEssenceTemplate.difficulty(70.0f); // not sure about difficulty. use of the essence will be pass/fail so power may not matter.
            treeEssenceTemplate.weightGrams(1000);
            treeEssenceTemplate.material((byte) 21); // 21 for magic or 14 for sprout(which is log or wood)
            treeEssenceTemplate.value(10000);
            treeEssenceTemplate.isTraded(true);
            try {
                treeEssenceTemplate.build();
            } catch (IOException e) {
                e.printStackTrace();
            }

            ItemTemplateBuilder bushEssenceTemplate = new ItemTemplateBuilder("jdbBushEssenceTemplate");
            bushEssenceTemplateId = IdFactory.getIdFor("jdbBushEssenceTemplate", IdType.ITEMTEMPLATE);
            bushEssenceTemplate.name("Bush essence", "Bush essence", "A mystical miniature bush. It's used to enchant or disenchant a bush tile.");
            bushEssenceTemplate.size(3);
            bushEssenceTemplate.itemTypes(new short[]{ItemTypes.ITEM_TYPE_MAGIC, ItemTypes.ITEM_TYPE_BULK});
            bushEssenceTemplate.imageNumber((short) 484); // using sprout image number.
            bushEssenceTemplate.behaviourType((short) 1); // item behaviour
            bushEssenceTemplate.combatDamage(0);
            bushEssenceTemplate.decayTime(86400L); // sprout decay time.
            bushEssenceTemplate.dimensions(10, 10, 10);
            bushEssenceTemplate.primarySkill(-10);
            bushEssenceTemplate.modelName("model.resource.sprout.");
            bushEssenceTemplate.difficulty(70.0f); // not sure about difficulty. use of the essence will be pass/fail so power may not matter.
            bushEssenceTemplate.weightGrams(1000);
            bushEssenceTemplate.material((byte) 21); // 21 for magic or 14 for sprout(which is log or wood)
            bushEssenceTemplate.value(10000);
            bushEssenceTemplate.isTraded(true);
            try {
                bushEssenceTemplate.build();
            } catch (IOException e) {
                e.printStackTrace();
            }

            ItemTemplateBuilder grassEssenceTemplate = new ItemTemplateBuilder("jdbGrassEssenceTemplate");
            grassEssenceTemplateId = IdFactory.getIdFor("jdbGrassEssenceTemplate", IdType.ITEMTEMPLATE);
            grassEssenceTemplate.name("Grass essence", "Grass essence", "A mystical clump of grass. It's used to enchant or disenchant a grass tile.");
            grassEssenceTemplate.size(3);
            grassEssenceTemplate.itemTypes(new short[]{ItemTypes.ITEM_TYPE_MAGIC, ItemTypes.ITEM_TYPE_BULK});
            grassEssenceTemplate.imageNumber((short) 702); // using mixed grass image number.
            grassEssenceTemplate.behaviourType((short) 1); // item behaviour
            grassEssenceTemplate.combatDamage(0);
            grassEssenceTemplate.decayTime(86400L); // sprout decay time.
            grassEssenceTemplate.dimensions(10, 10, 10);
            grassEssenceTemplate.primarySkill(-10);
            grassEssenceTemplate.modelName("model.flower.mixedgrass.");
            grassEssenceTemplate.difficulty(70.0f); // not sure about difficulty. use of the essence will be pass/fail so power may not matter.
            grassEssenceTemplate.weightGrams(1000);
            grassEssenceTemplate.material((byte) 21); // 21 for magic or 14 for sprout(which is log or wood)
            grassEssenceTemplate.value(10000);
            grassEssenceTemplate.isTraded(true);
            try {
                grassEssenceTemplate.build();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void enchantedInIsGrassType() throws NotFoundException, CannotCompileException {
        JAssistClassData tilesClass = JAssistClassData.getClazz("Tiles");
        if (tilesClass == null)
            tilesClass = new JAssistClassData("com.wurmonline.mesh.Tiles", classPool);
        JAssistMethodData isGrassTypeWU = new JAssistMethodData(tilesClass, "(B)Z", "isGrassType");
        CtMethod isGrassTypeMod = classPool.get("com.joedobo27.enchantednature.EnchantedNatureMod").getMethod("isGrassType",
                "(B)Z");
        isGrassTypeWU.getCtMethod().setBody(isGrassTypeMod, null);
    }

    private boolean makeEnchantedGrassInTile() {
        try {
            Field isNormal = ReflectionUtil.getField(Tiles.Tile.class, "isNormal");
            for (Tiles.Tile tile : Tiles.Tile.getTiles()) {
                if (tile != null && tile.getId() == 2) {
                    ReflectionUtil.setPrivateField(tile, isNormal, Boolean.TRUE);
                }
            }
        }catch (NoSuchFieldException | IllegalAccessException e){
            return false;
        }
        return true;
    }

    private boolean cutGrassInTile() {
        try {
            Field isGrass = ReflectionUtil.getField(Tiles.Tile.class, "isGrass");
            for (Tiles.Tile tile : Tiles.Tile.getTiles()) {
                if (tile != null && tile.getId() == 13) {
                    ReflectionUtil.setPrivateField(tile, isGrass, Boolean.TRUE);
                }
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return false;
        }
        return true;
    }

    /**
     * Change TreeTileBehavior.getNatureActions() so cut grass on tree tile behaviour is possible.
     * was-
     *   if (!theTile.isEnchanted() && growthStage != GrassData.GrowthTreeStage.LAWN) {...}
     *
     * becomes-
     *   change the return value of isEnchanted() to always be false.
     *
     * Bytecode index 527 which goes with line 239.
     *
     * @return boolean type, isSuccess.
     * @throws NotFoundException JA related, forwarded.
     * @throws CannotCompileException JA related, forwarded.
     */
    private boolean cutGrassInGetNatureActions() throws NotFoundException, CannotCompileException {
        final boolean[] successes = {false};
        if (!cutGrass)
            return successes[0];
        JAssistClassData tileTreeBehaviour = JAssistClassData.getClazz("TileTreeBehaviour");
        if (tileTreeBehaviour == null)
            tileTreeBehaviour = new JAssistClassData("com.wurmonline.server.behaviours.TileTreeBehaviour", classPool);
        JAssistMethodData getNatureActions = new JAssistMethodData(tileTreeBehaviour,
                "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IILcom/wurmonline/mesh/Tiles$Tile;B)Ljava/util/List;",
                "getNatureActions");
        getNatureActions.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException{
                if (Objects.equals("isEnchanted", methodCall.getMethodName()) && methodCall.getLineNumber() == 239) {
                    successes[0] = true;
                    methodCall.replace("$_ = false;");
                }
            }
        });
        return successes[0];
    }

    /**
     * Editing TilePoller.checkEffects() with bytecode alteration. Make it so enchanted grass will be checked for grass growth.
     * was-
     *   if (type == Tiles.Tile.TILE_GRASS.id || type == Tiles.Tile.TILE_KELP.id || type == Tiles.Tile.TILE_REED.id) {...)
     *
     * becomes-
     *   if (type == Tiles.Tile.TILE_ENCHANTED_GRASS.id || type == Tiles.Tile.TILE_GRASS.id || type == Tiles.Tile.TILE_KELP.id
     *   || type == Tiles.Tile.TILE_REED.id) {...}
     *
     * @return boolean type, isSuccess
     * @throws NotFoundException JA related, forwarded.
     */
    private boolean grassGrowthOnEnchantedGrass() throws NotFoundException {
        JAssistClassData tilePoller = new JAssistClassData("com.wurmonline.server.zones.TilePoller", classPool);
        JAssistMethodData checkEffects = new JAssistMethodData(tilePoller, "(IIIBB)V", "checkEffects");

        //<editor-fold desc="find bytecode from Javap.exe">
        /*
           221: iload_3
           222: getstatic     #94                 // Field com/wurmonline/mesh/Tiles$Tile.TILE_GRASS:Lcom/wurmonline/mesh/Tiles$Tile;
           225: getfield      #49                 // Field com/wurmonline/mesh/Tiles$Tile.id:B
           228: if_icmpeq     251
           231: iload_3
           232: getstatic     #95                 // Field com/wurmonline/mesh/Tiles$Tile.TILE_KELP:Lcom/wurmonline/mesh/Tiles$Tile;
           235: getfield      #49                 // Field com/wurmonline/mesh/Tiles$Tile.id:B
           238: if_icmpeq     251
           241: iload_3
           242: getstatic     #96                 // Field com/wurmonline/mesh/Tiles$Tile.TILE_REED:Lcom/wurmonline/mesh/Tiles$Tile;
           245: getfield      #49                 // Field com/wurmonline/mesh/Tiles$Tile.id:B
           248: if_icmpne     318
        */
        //</editor-fold>

        Bytecode find = new Bytecode(tilePoller.getConstPool());
        find.addOpcode(Opcode.ILOAD_3);
        find.addOpcode(Opcode.GETSTATIC);
        byte[] poolAddress = findConstantPoolReference(tilePoller.getConstPool(),
                "// Field com/wurmonline/mesh/Tiles$Tile.TILE_GRASS:Lcom/wurmonline/mesh/Tiles$Tile;");
        find.add(poolAddress[0], poolAddress[1]);
        find.addOpcode(Opcode.GETFIELD);
        poolAddress = findConstantPoolReference(tilePoller.getConstPool(),
                "// Field com/wurmonline/mesh/Tiles$Tile.id:B");
        find.add(poolAddress[0], poolAddress[1]);
        find.addOpcode(Opcode.IF_ICMPEQ); // 228: if_icmpeq     251
        poolAddress = BytecodeTools.intToByteArray(23, 2);
        find.add(poolAddress[0], poolAddress[1]);
        find.addOpcode(Opcode.ILOAD_3);
        find.addOpcode(Opcode.GETSTATIC);
        poolAddress = findConstantPoolReference(tilePoller.getConstPool(),
                "// Field com/wurmonline/mesh/Tiles$Tile.TILE_KELP:Lcom/wurmonline/mesh/Tiles$Tile;");
        find.add(poolAddress[0], poolAddress[1]);
        find.addOpcode(Opcode.GETFIELD);
        poolAddress = findConstantPoolReference(tilePoller.getConstPool(),
                "// Field com/wurmonline/mesh/Tiles$Tile.id:B");
        find.add(poolAddress[0], poolAddress[1]);
        find.addOpcode(Opcode.IF_ICMPEQ); // 238: if_icmpeq     251
        poolAddress = BytecodeTools.intToByteArray(13, 2);
        find.add(poolAddress[0], poolAddress[1]);
        find.addOpcode(Opcode.ILOAD_3);
        find.addOpcode(Opcode.GETSTATIC);
        poolAddress = findConstantPoolReference(tilePoller.getConstPool(),
                "// Field com/wurmonline/mesh/Tiles$Tile.TILE_REED:Lcom/wurmonline/mesh/Tiles$Tile;");
        find.add(poolAddress[0], poolAddress[1]);
        find.addOpcode(Opcode.GETFIELD);
        poolAddress = findConstantPoolReference(tilePoller.getConstPool(),
                "// Field com/wurmonline/mesh/Tiles$Tile.id:B");
        find.add(poolAddress[0], poolAddress[1]);
        find.addOpcode(Opcode.IF_ICMPNE); // 248: if_icmpne     318
        poolAddress = BytecodeTools.intToByteArray(70, 2);
        find.add(poolAddress[0], poolAddress[1]);


        Bytecode replace = new Bytecode(tilePoller.getConstPool());
        replace.addOpcode(Opcode.ILOAD_3);
        replace.addOpcode(Opcode.GETSTATIC);
        poolAddress = addConstantPoolReference(tilePoller.getConstPool(),
                "// Field com/wurmonline/mesh/Tiles$Tile.TILE_ENCHANTED_GRASS:Lcom/wurmonline/mesh/Tiles$Tile;");
        replace.add(poolAddress[0], poolAddress[1]);
        replace.addOpcode(Opcode.GETFIELD);
        poolAddress = findConstantPoolReference(tilePoller.getConstPool(),
                "// Field com/wurmonline/mesh/Tiles$Tile.id:B");
        replace.add(poolAddress[0], poolAddress[1]);
        replace.addOpcode(Opcode.IF_ICMPEQ); // 218: if_icmpeq     251
        poolAddress = BytecodeTools.intToByteArray(33, 2);
        replace.add(poolAddress[0], poolAddress[1]);
        replace.addOpcode(Opcode.ILOAD_3);
        replace.addOpcode(Opcode.GETSTATIC);
        poolAddress = findConstantPoolReference(tilePoller.getConstPool(),
                "// Field com/wurmonline/mesh/Tiles$Tile.TILE_GRASS:Lcom/wurmonline/mesh/Tiles$Tile;");
        replace.add(poolAddress[0], poolAddress[1]);
        replace.addOpcode(Opcode.GETFIELD);
        poolAddress = findConstantPoolReference(tilePoller.getConstPool(),
                "// Field com/wurmonline/mesh/Tiles$Tile.id:B");
        replace.add(poolAddress[0], poolAddress[1]);
        replace.addOpcode(Opcode.IF_ICMPEQ); // 228: if_icmpeq     251
        poolAddress = BytecodeTools.intToByteArray(23, 2);
        replace.add(poolAddress[0], poolAddress[1]);
        replace.addOpcode(Opcode.ILOAD_3);
        replace.addOpcode(Opcode.GETSTATIC);
        poolAddress = findConstantPoolReference(tilePoller.getConstPool(),
                "// Field com/wurmonline/mesh/Tiles$Tile.TILE_KELP:Lcom/wurmonline/mesh/Tiles$Tile;");
        replace.add(poolAddress[0], poolAddress[1]);
        replace.addOpcode(Opcode.GETFIELD);
        poolAddress = findConstantPoolReference(tilePoller.getConstPool(),
                "// Field com/wurmonline/mesh/Tiles$Tile.id:B");
        replace.add(poolAddress[0], poolAddress[1]);
        replace.addOpcode(Opcode.IF_ICMPEQ); // 238: if_icmpeq     251
        poolAddress = BytecodeTools.intToByteArray(13, 2);
        replace.add(poolAddress[0], poolAddress[1]);
        replace.addOpcode(Opcode.ILOAD_3);
        replace.addOpcode(Opcode.GETSTATIC);
        poolAddress = findConstantPoolReference(tilePoller.getConstPool(),
                "// Field com/wurmonline/mesh/Tiles$Tile.TILE_REED:Lcom/wurmonline/mesh/Tiles$Tile;");
        replace.add(poolAddress[0], poolAddress[1]);
        replace.addOpcode(Opcode.GETFIELD);
        poolAddress = findConstantPoolReference(tilePoller.getConstPool(),
                "// Field com/wurmonline/mesh/Tiles$Tile.id:B");
        replace.add(poolAddress[0], poolAddress[1]);
        replace.addOpcode(Opcode.IF_ICMPNE); // 248: if_icmpne     318
        poolAddress = BytecodeTools.intToByteArray(70, 2);
        replace.add(poolAddress[0], poolAddress[1]);

        CodeReplacer codeReplacer = new CodeReplacer(checkEffects.getCodeAttribute());
        try {
            codeReplacer.replaceCode(find.get(), replace.get());
            checkEffects.getMethodInfo().rebuildStackMapIf6(classPool, tilePoller.getClassFile());
        }catch (BadBytecode e){
            return false;
        }
        return true;
    }

    /**
     * change Terraforming.pickSprout() is the pick sprout action works on enchanted trees.
     * was-
     *   if (sickle.getTemplateId() == 267 && !theTile.isEnchanted()) {
     *
     * becomes-
     *   Alter isEnchanted() so it always returns false;
     *
     * Bytecode index 22, line number group 4843. isEnchanted methods isn't used again in method so not using line number.
     *
     * @return boolean type, isSuccess
     * @throws NotFoundException JA related, forwarded.
     * @throws CannotCompileException JA related, forwarded.
     */
    private boolean pickSproutInPickSprout() throws NotFoundException, CannotCompileException{
        final boolean[] successes = {false};
        if (!pickSprouts)
            return successes[0];
        JAssistClassData terraforming = JAssistClassData.getClazz("Terraforming");
        if (terraforming == null) {
            terraforming = new JAssistClassData(
                    "com.wurmonline.server.behaviours.Terraforming", classPool);
        }
        JAssistMethodData pickSprout = new JAssistMethodData(terraforming,
                "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIILcom/wurmonline/mesh/Tiles$Tile;FLcom/wurmonline/server/behaviours/Action;)Z",
                "pickSprout"
        );
        pickSprout.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException{
                if (Objects.equals("isEnchanted", methodCall.getMethodName())){
                    successes[0] = true;
                    methodCall.replace("$_ = false;");
                }
            }
        });
        return successes[0];
    }

    /**
     * was-
     *   else if (!theTile.isEnchanted() && action == 187) {
     *
     * becomes-
     *   Alter isEnchanted() so it always returns false;
     *
     * Bytecode index 113 goes on line 557
     *
     * @return boolean type, isSuccess
     * @throws NotFoundException JA related, forwarded.
     * @throws CannotCompileException JA related, forwarded.
     */
    private boolean pickSproutInAction() throws NotFoundException, CannotCompileException {
        final boolean[] successes = {false};
        if (!pickSprouts)
            return successes[0];
        JAssistClassData tileTreeBehaviour = JAssistClassData.getClazz("TileTreeBehaviour");
        if (tileTreeBehaviour == null) {
            tileTreeBehaviour = new JAssistClassData(
                    "com.wurmonline.server.behaviours.TileTreeBehaviour", classPool);
        }
        JAssistMethodData action = new JAssistMethodData(tileTreeBehaviour,
        "(Lcom/wurmonline/server/behaviours/Action;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IIZIISF)Z",
                "action");
        action.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("isEnchanted", methodCall.getMethodName()) && methodCall.getLineNumber() == 557) {
                    successes[0] = true;
                    methodCall.replace("$_ = false;");
                }
            }
        });
        return successes[0];
    }

    /**
     * Change TileTreeBehaviour.getNatureActions() so pick sprout on tree tile behaviour is possible.
     * was-
     *   if (subject.getTemplateId() == 267 && !theTile.isEnchanted()) {
     *
     * becomes-
     *   change the return value of isEnchanted() to always be false.
     *
     * Bytecode index 39 which goes with line 178.
     *
     * @return boolean type, isSuccess.
     * @throws NotFoundException JA related, forwarded.
     * @throws CannotCompileException JA related, forwarded.
     */
    private boolean pickSproutInGetNatureActions() throws NotFoundException, CannotCompileException {
        final boolean[] successes = {false};
        if (!pickSprouts)
            return successes[0];
        JAssistClassData tileTreeBehaviour = JAssistClassData.getClazz("TileTreeBehaviour");
        if (tileTreeBehaviour == null) {
            tileTreeBehaviour = new JAssistClassData("com.wurmonline.server.behaviours.TileTreeBehaviour", classPool);
        }
        JAssistMethodData getNatureActions = new JAssistMethodData(tileTreeBehaviour,
                "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;IILcom/wurmonline/mesh/Tiles$Tile;B)Ljava/util/List;",
                "getNatureActions");
        getNatureActions.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("isEnchanted", methodCall.getMethodName()) && methodCall.getLineNumber() == 178) {
                    successes[0] = true;
                    methodCall.replace("$_ = false;");
                }
            }
        });

        return successes[0];
    }

    /**
     * was-
     *   final int chance = TilePoller.entryServer ? Server.rand.nextInt(20) : Server.rand.nextInt(225);
     *
     * becomes -
     *   Change the 255 arg to 64 if theTile.isEnchanted() is true. This should x4 the chance for an enchanted
     *   tree to age.
     *
     * Bytecode line 131.
     *
     * @return boolean type, isSuccess.
     * @throws NotFoundException JA related, forwarded.
     * @throws CannotCompileException JA related, forwarded.
     */
    private boolean fasterGrowthInCheckForTreeGrowth() throws NotFoundException, CannotCompileException {
        final boolean[] successes = {false};
        if (!fasterTreeGrowth)
            return successes[0];
        JAssistClassData tilePoller = JAssistClassData.getClazz("TilePoller");
        if (tilePoller == null) {
            tilePoller = new JAssistClassData("com.wurmonline.server.zones.TilePoller", classPool);
        }
        String growthValue = Integer.toString(255 / growthAccelerator);
        JAssistMethodData checkForTreeGrowth = new JAssistMethodData(tilePoller, "(IIIBB)V", "checkForTreeGrowth");
        checkForTreeGrowth.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("nextInt", methodCall.getMethodName()) && methodCall.indexOfBytecode() == 131) {
                    successes[0] = true;
                    methodCall.replace("" +
                            "{" +
                                "$1 = theTile.isEnchanted() ? " + growthValue + " : 255;" +
                                "$_ = $proceed($$);" +
                            "}");
                }
            }
        });
        return successes[0];
    }

    /**
     * was-
     *   final byte newType2 = convertToNewType(theTile, newData2);
     *
     * becomes-
     *   Change the value passed in for newData2 if the tree's age is 14 (overaged). Recalculate tree age using age 13(very old, sprout):
     *   newData2 = (byte)((age << 4) + partdata & 0xFF);
     *
     * Bytecode index 391 which goes in line number 1940. Note that in bytecode the local var is newData, not newData2.
     *
     * @return boolean type, isSuccess.
     * @throws NotFoundException JA related, forwarded.
     * @throws CannotCompileException JA related, forwarded.
     */
    private boolean noOverageInCheckForTreeGrowth() throws NotFoundException, CannotCompileException {
        final boolean[] successes = {false};
        if (!noOverageStage)
            return successes[0];
        JAssistClassData tilePoller = JAssistClassData.getClazz("TilePoller");
        if (tilePoller == null) {
            tilePoller = new JAssistClassData("com.wurmonline.server.zones.TilePoller", classPool);
        }
        JAssistMethodData checkForTreeGrowth = new JAssistMethodData(tilePoller, "(IIIBB)V", "checkForTreeGrowth");
        checkForTreeGrowth.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                if (Objects.equals("convertToNewType", methodCall.getMethodName()) && methodCall.getLineNumber() == 1940) {
                    successes[0] = true;
                    methodCall.replace("" +
                            "{" +
                                "newData = (age == 14 && theTile.isEnchanted()) ? (byte)((13 << 4) + partdata & 0xFF) : newData;" +
                                "$_ = $proceed($$);" +
                            "}");
                }
            }
        });
        return successes[0];
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
     * @return boolean type, isSuccess.
     * @throws NotFoundException JA related, forwarded.
     * @throws CannotCompileException JA related, forwarded.
     */
    private boolean disablePackingInGrazeNonCorrupt() throws NotFoundException, CannotCompileException {
        final boolean[] successes = {false};
        if (!grazeNeverPacks)
            return successes[0];
        JAssistClassData creature = new JAssistClassData("com.wurmonline.server.creatures.Creature", classPool);
        JAssistMethodData grazeNonCorrupt = new JAssistMethodData(creature,
                "(IBLcom/wurmonline/server/villages/Village;)Z", "grazeNonCorrupt");
        grazeNonCorrupt.getCtMethod().instrument(new ExprEditor() {
            @Override
            public void edit(FieldAccess fieldAccess) throws CannotCompileException {
                if (Objects.equals("id", fieldAccess.getFieldName()) && fieldAccess.indexOfBytecode() == 167) {
                    successes[0] = true;
                    fieldAccess.replace("$_ = type == com.wurmonline.mesh.Tiles.Tile.TILE_ENCHANTED_GRASS.id ? 0 : type;");
                }
            }
        });
        return successes[0];
    }

    private void addCreationForNatureEssences() {
        AdvancedCreationEntry treeEssenceTemplate = CreationEntryCreator.createAdvancedEntry(SkillList.ALCHEMY_NATURAL,
                ItemList.fruitJuice, ItemList.sprout, treeEssenceTemplateId, false,false,
                0.0f, true, false, CreationCategories.ALCHEMY);
        treeEssenceTemplate.addRequirement(new CreationRequirement(1, ItemList.sourceSalt, 1, true));
        treeEssenceTemplate.addRequirement(new CreationRequirement(2, ItemList.scrapwood, 1, true));

        AdvancedCreationEntry bushEssenceTemplate = CreationEntryCreator.createAdvancedEntry(SkillList.ALCHEMY_NATURAL,
                ItemList.fruitJuice, ItemList.sprout, bushEssenceTemplateId, false, false,
                0.0f, true, false, CreationCategories.ALCHEMY);
        bushEssenceTemplate.addRequirement(new CreationRequirement(1, ItemList.sourceSalt, 1, true));
        bushEssenceTemplate.addRequirement(new CreationRequirement(2, ItemList.flowerRose, 1, true));

        AdvancedCreationEntry grassEssenceTemplate = CreationEntryCreator.createAdvancedEntry(SkillList.ALCHEMY_NATURAL,
                ItemList.fruitJuice, ItemList.mixedGrass, grassEssenceTemplateId, false, false,
                0.0f, true, false, CreationCategories.ALCHEMY);
        grassEssenceTemplate.addRequirement(new CreationRequirement(1, ItemList.sourceSalt, 1, true));
        grassEssenceTemplate.addRequirement(new CreationRequirement(2, ItemList.mixedGrass, 1, true));
    }

    static int getTreeEssenceTemplateId() {
        return treeEssenceTemplateId;
    }

    static int getBushEssenceTemplateId() {
        return bushEssenceTemplateId;
    }

    static int getGrassEssenceTemplateId() {
        return grassEssenceTemplateId;
    }

    @SuppressWarnings("unused")
    public static boolean isGrassType(final byte tileId) {
        switch (tileId & 0xFF) {
            case Tiles.TILE_TYPE_GRASS:
            case Tiles.TILE_TYPE_ENCHANTED_GRASS:
            case Tiles.TILE_TYPE_KELP:
            case Tiles.TILE_TYPE_REED:
            case Tiles.TILE_TYPE_LAWN: {
                return true;
            }
            default: {
                return false;
            }
        }
    }

    static {
        logger = Logger.getLogger(EnchantedNatureMod.class.getName());
        classPool = HookManager.getInstance().getClassPool();
    }
}
