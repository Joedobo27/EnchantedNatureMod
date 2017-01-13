package com.joedobo27.enchantednature;

import com.wurmonline.server.Items;
import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.items.Item;

class Wrap {

    @SuppressWarnings("unused")
    enum Actions {
        QUICK(0,""),
        NEED_FOOD(1,"Action blocked if food is too low."),
        SPELL(2,""),
        ATTACK(3,""),
        FATIGUE(4,""),
        POLICED(5,""),
        NOMOVE(6,"Actions is cancelled if toon moves."),
        NON_LIBILAPRIEST(7,""),
        NON_WHITEPRIEST(8,""),
        NON_RELIGION(9,"Doing Actions are considered unfaithful."),
        ATTACK_HIGH(12,""),
        ATTACK_LOW(13,""),
        ATTACK_LEFT(14,""),
        ATTACK_RIGHT(15,""),
        DEFEND(16,""),
        STANCE_CHANGE(17,""),
        ALLOW_MAGRANON(18,""),
        ALLOW_FO(19,""),
        ALLOW_VYNORA(20,""),
        ALLOW_LIBILA(21,""),
        NO_OPPORTUNITY(22,""),
        IGNORERANGE(23,""),
        VULNERABLE(24,""),
        MISSION(25,""),
        NOTVULNERABLE(26,""),
        NONSTACKABLE(27,""),
        NONSTACKABLE_FIGHT(28,""),
        BLOCKED_NONE(29,""),
        BLOCKED_FENCE(30,""),
        BLOCKED_WALL(31,""),
        BLOCKED_FLOOR(32,""),
        BLOCKED_ALL_BUT_OPEN(33,""),
        BLOCKED_TARGET_TILE(34,""),
        MAYBE_USE_ACTIVE_ITEM(35,""),
        ALWAYS_USE_ACTIVE_ITEM(36,""),
        NEVER_USE_ACTIVE_ITEM(37,""),
        ALLOW_MAGRANON_IN_CAVE(38,""),
        ALLOW_FO_ON_SURFACE(39,""),
        ALLOW_LIBILA_IN_CAVE(40,""),
        USES_NEW_SKILL_SYSTEM(41,""),
        VERIFIED_NEW_SKILL_SYSTEM(42,""),
        SHOW_ON_SELECT_BAR(43,""),
        SAME_BRIDGE(44,""),
        PERIMETER(45,""),
        CORNER(46,""),
        ENEMY_NEVER(47,""),
        ENEMY_ALWAYS(48,""),
        ENEMY_NO_GUARDS(49,""),
        BLOCKED_NOT_DOOR(50,"");

        private final int id;
        private final String description;

        Actions(int actionID, String description){
            this.id = actionID;
            this.description = description;
        }

        public int getId() {
            return id;
        }
    }

    @SuppressWarnings("unused")
    enum Rarity {
        NO_RARITY(0, null),
        RARE(1, "1 in 1"),
        SUPREME(2, "1 in 34"),
        FANTASTIC(3, "1 in 9709");

        /*
        Player getRarity()
        supreme 1 in 33.334 chance ... 3/100=33.334 for Paying. 1 in 100 chance for F2P
        fantastic 1 in 9708.737 chance ... 1.03f/10,000; 103 in 1,000,000 for Paying. 1 in 10,000 chance for F2P.

        improvement has a 1 in 5 chance to go rare if: the power/success of action is > 0, and the action's rarity
        is greater then the rarity of the item.

        */
        private final int id;
        private final String chance;

        Rarity(int id, String chance){
            this.id = id;
            this.chance = chance;
        }

        byte getId() {return (byte)id;
        }

        String getChance() {return chance;}
    }

    static Item getItemFromID(long id){
        try {
            return Items.getItem(id);
        }catch (NoSuchItemException e){
            return null;
        }
    }

    static int getTemplateWeightFromItem(Item item){
        return item.getTemplate().getWeightGrams();
    }
}
