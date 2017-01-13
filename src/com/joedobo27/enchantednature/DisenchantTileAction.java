package com.joedobo27.enchantednature;


import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.Players;
import com.wurmonline.server.Server;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.behaviours.Methods;
import com.wurmonline.server.behaviours.NoSuchActionException;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.skills.Skills;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.joedobo27.enchantednature.Wrap.Actions.*;

public class DisenchantTileAction implements ModAction, BehaviourProvider, ActionPerformer {
    private static final Logger logger = Logger.getLogger(EnchantedNatureMod.class.getName());

    private final short actionId;
    private final ActionEntry actionEntry;

    DisenchantTileAction() {
        actionId = (short) ModActions.getNextActionId();
        actionEntry = ActionEntry.createEntry(actionId, "Disenchant", "Disenchanting", new int[]{POLICED.getId(), NON_RELIGION.getId(),
                ALLOW_FO.getId(), ALWAYS_USE_ACTIVE_ITEM.getId()});
        ModActions.registerAction(actionEntry);
    }

    @Override
    public short getActionId() {
        return actionId;
    }

    @Override
    public List<ActionEntry> getBehavioursFor(Creature performer, Item activeItem, int tileX, int tileY, boolean onSurface, int tileId) {
        byte tileType = Tiles.decodeType(tileId);
        Tiles.Tile theTile = Tiles.getTile(tileType);
        if (Methods.isActionAllowed(performer, actionId) && theTile.isEnchanted() && isCorrectEssence(activeItem, theTile)){
            return Collections.singletonList(actionEntry);
        } else
            return null;
    }

    @Override
    public boolean action(Action action, Creature performer, Item activeItem, int tileX, int tileY, boolean onSurface, int heightOffset, int tileId, short actionId, float counter) {
        byte tileType = Tiles.decodeType(tileId);
        Tiles.Tile theTile = Tiles.getTile(tileType);
        if (performer instanceof Player && theTile.isEnchanted() && isCorrectEssence(activeItem, theTile)) {
            try {
                int time;
                final float ACTION_START_TIME = 1.0f;
                if (counter == ACTION_START_TIME) {
                    performer.getCommunicator().sendNormalServerMessage("You start " + action.getActionEntry().getVerbString() + ".");
                    Server.getInstance().broadCastAction(performer.getName() + " starts to " + action.getActionString() + ".", performer, 5);
                    time = 50;
                    performer.getCurrentAction().setTimeLeft(time);
                    performer.sendActionControl(action.getActionEntry().getVerbString(), true, time);
                    return false;
                }
                if (isActionDone(performer.getCurrentAction().getTimeLeft(), counter)) {
                    // do skill check for Gardening
                    Skills skills = performer.getSkills();
                    Skill gardening = skills.getSkillOrLearn(SkillList.GARDENING);
                    gardening.skillCheck(1, activeItem, 0, false, counter);
                    // destroy plant essence
                    activeItem.setWeight(0, true);
                    // convert tile
                    disenchantNature(performer, tileX, tileY, onSurface, tileId);
                    return true;
                }
            } catch (NoSuchActionException e){
                logger.log(Level.WARNING, "Performer doesn't have an action.", e);
                return true;
            }
        }
        return false;
    }

    private boolean isActionDone(int time, float counter){
        final float CONVERT_TIME_TO_COUNTER_DIVISOR = 10.0f;
        return time - (counter * CONVERT_TIME_TO_COUNTER_DIVISOR) <= 0f;
    }

    private boolean isCorrectEssence(Item subject, Tiles.Tile theTile) {
        return theTile.isTree() && subject.getTemplateId() == EnchantedNatureMod.getTreeEssenceTemplateId() ||
                theTile.isBush() && subject.getTemplateId() == EnchantedNatureMod.getBushEssenceTemplateId() ||
                theTile.isGrass() && subject.getTemplateId() == EnchantedNatureMod.getGrassEssenceTemplateId();
    }

    private void disenchantNature(Creature performer, int tileX, int tileY, boolean onSurface, int tileId){
        byte tileType = Tiles.decodeType(tileId);
        Tiles.Tile theTile = Tiles.getTile(tileType);
        byte tileData = Tiles.decodeData(tileId);
        byte enchantedTileType = tileType;
        if (theTile.isGrass() && theTile.isEnchanted())
            enchantedTileType = Tiles.Tile.TILE_GRASS.id;
        if (theTile.isEnchantedTree())
            enchantedTileType = theTile.getTreeType(tileData).asNormalTree();
        if (theTile.isEnchantedBush())
            enchantedTileType = theTile.getBushType(tileData).asNormalBush();

        Server.setSurfaceTile(tileX, tileY, Tiles.decodeHeight(tileId), enchantedTileType, tileData);
        performer.getMovementScheme().touchFreeMoveCounter();
        Players.getInstance().sendChangedTile(tileX, tileY, onSurface, false);
    }
}
