package maingame;

import akka.actor.ActorRef;
import com.fasterxml.jackson.databind.JsonNode;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.Card;
import structures.basic.EffectAnimation;
import structures.basic.Player;
import structures.basic.Tile;
import structures.basic.Unit;
import structures.basic.Position;
import structures.basic.UnitAnimationType;
import utils.BasicObjectBuilders;
import utils.OrderedCardLoader;
import utils.StaticConfFiles;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class GameLoop {

    private static Player m_humanPlayer;
    private static Player m_aiPlayer;

    private static int  m_curturn;
    private static int  m_turn;  //0:Player   1:Ai
    private static int  m_currentstate;// 0 : noclick  1:clickunit  2:attack  3:clickcard

    private static  int  m_clickcardid;
    private static  int   m_clickunitposx,m_clickunitposy;
    private static ArrayList<TileUnit> m_unitPlayerList = new ArrayList<>();
    private static ArrayList<TileUnit> m_unitAiList = new ArrayList<>();
    private static List<Card> m_PlayerCards ;
    private static List<Card> m_AiCards ;
    public static void executeInit(ActorRef out, GameState gameState, JsonNode message) {
        m_curturn = 1;
        BasicCommands.addPlayer1Notification(out, "Player start playing", 3);
        m_humanPlayer = new Player(20, 2);
        m_aiPlayer =  new Player(20, 2);
        m_PlayerCards = OrderedCardLoader.getPlayer2Cards(1);
        m_AiCards = OrderedCardLoader.getPlayer1Cards(1);
        Random random = new Random();
        for(Card item : m_PlayerCards){item.setIsCreature(true);}
        for(Card item : m_AiCards){item.setIsCreature(true);}
        int  index=3;
        while(index-- > 0){
            Card  c = m_PlayerCards.get(random.nextInt(m_PlayerCards.size()));
            c.setIsCreature(false);
        }
        index=2;
        while(index-- > 0){
            Card  c = m_AiCards.get(random.nextInt(m_AiCards.size()));
            c.setIsCreature(false);
        }

        m_unitPlayerList.clear();
        TileUnit unit = new TileUnit();
        unit.id = 0;unit.isPlayer=1;
        unit.tilex=1;unit.tiley=2;
        unit.intAttack = 2;unit.intHealth=20;
        unit.strCardName = ""; //<0:main
        unit.isShow = false;
        unit.isMove = false;
        unit.cur_unit = BasicObjectBuilders.loadUnit(StaticConfFiles.aiAvatar, unit.id, Unit.class);;
        m_unitPlayerList.add(unit);

        m_unitAiList.clear();
        TileUnit unit1 = new TileUnit();
        unit1.id = 100;unit1.isPlayer=1;
        unit1.tilex=7;unit1.tiley=2;
        unit1.intAttack = 2;unit1.intHealth=20;
        unit1.strCardName = ""; //<0:main
        unit1.isShow = false;
        unit1.isMove = false;
        unit1.cur_unit = BasicObjectBuilders.loadUnit(StaticConfFiles.humanAvatar, unit1.id, Unit.class);;
        m_unitAiList.add(unit1);

        m_turn = 0;
        m_currentstate = 0;
        refreshBoard(out);
    }

    public static void EndTurn(ActorRef out, GameState gameState){
        if(m_turn == 0){
            m_turn = 1; //AI starts playing
            m_currentstate = 0;
            m_aiPlayer.setMana(m_aiPlayer.getMana()+1);
            for(TileUnit item:m_unitAiList){item.isMove = false;}
            Random random = new Random();
            Card  c = m_AiCards.get(random.nextInt(m_AiCards.size()));c.setIsCreature(false);
            BasicCommands.addPlayer1Notification(out, "AI starts playing", 3);
            refreshBoard(out);
            try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
            //If card 1 can summon, summon Monster 1.
            int  id = getCardNo(m_AiCards,1);
            c = m_AiCards.get(id);
            if(c != null && !c.isCreature()){// summon Monster
                if(c.getManacost() <= m_aiPlayer.getMana()){
                    //click No.1 card
                    highlight_card(out,1,gameState,null);
                    try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                    //The master will summon random locations around
                    TileUnit  t = m_unitAiList.get(0);
                    TiltPos   p = getRoundRandomFreePos(t.tilex,t.tiley);
                    if(p != null){//summon
                        //highlight_tile(out,p.tilex,p.tiley,gameState,null);
                        //try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                        TileUnit unit1 = new TileUnit();
                        unit1.id =100+m_unitAiList.size();unit1.isPlayer=0;
                        unit1.tilex=p.tilex;unit1.tiley=p.tiley;
                        unit1.intAttack = 2;unit1.intHealth=c.getManacost();
                        c.setIsCreature(true);
                        BasicCommands.addPlayer1Notification(out, "Calling", 3);
                        unit1.cur_unit = BasicObjectBuilders.loadUnit(c.getUnitConfig(), unit1.id, Unit.class);
                        unit1.isShow = false;
                        unit1.isMove = false;
                        int curMana = m_aiPlayer.getMana()-c.getManacost();
                        if (curMana<0){curMana=0;}
                        m_aiPlayer.setMana(curMana);
                        m_unitAiList.add(unit1);
                        System.out.println("Ai call");
                    }
                }
            }
            //Every monster in action
            for(TileUnit  item : m_unitAiList){
                if(item.intHealth <= 0)continue;
                highlight_tile(out,item.tilex,item.tiley,gameState,null); //Click monster
                try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                //Look for attack opportunities

                TiltPos  pos = getAiNextPos(item.tilex,item.tiley);
                if(pos != null){
                    highlight_tile(out,pos.tilex,pos.tiley,gameState,null); //Click monster
                    try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                    //attack
                    System.out.println("go=" + item.tilex+":"+item.tiley + "--" + pos.tilex +":" + pos.tiley);

                    for(TileUnit p:m_unitPlayerList){
                        if(p.intHealth <= 0)continue;
                        System.out.println("show=" + p.tilex+":"+p.tiley + "--" + pos.tilex +":" + pos.tiley);
//                        System.out.println("out=" + (p.tilex >= (pos.tilex-1) && p.tilex<=(pos.tilex+1)
//                                && p.tiley >= (pos.tiley-1) && p.tiley <= (pos.tiley+1)));
                        if(p.tilex >= (pos.tilex-1) && p.tilex<=(pos.tilex+1)
                                && p.tiley >= (pos.tiley-1) && p.tiley <= (pos.tiley+1)) {//攻击
                            System.out.println("Ai Attack"+pos.tilex+":"+pos.tiley+" to " + p.tilex + ":" + p.tiley);
                            highlight_tile(out,p.tilex,p.tiley,gameState,null); //点击怪物
                            try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                            if(!gameState.something){return;}
                            break;
                        }
                    }
                    m_currentstate = 0;
                    refreshBoard(out);
                }
            }
            try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();}


            m_turn = 0;
            m_currentstate = 0;
            m_humanPlayer.setMana(m_humanPlayer.getMana()+1);
            c = m_PlayerCards.get(random.nextInt(m_PlayerCards.size()));c.setIsCreature(false);
            for(TileUnit item: m_unitPlayerList){item.isMove = false;}
            refreshBoard(out);
            BasicCommands.addPlayer1Notification(out, "Player start playing", 3);
            m_curturn ++;
        }
    }

    public static TiltPos getAiNextPos(int mainx,int mainy){
        Random random = new Random();
        List<TiltPos> possiblePositions = new ArrayList<>();
        List<TiltPos> allFreePositions = new ArrayList<>();
        for (int i = mainx - 2; i <= mainx + 2; i++) {
            for (int j = mainy - 2; j <= mainy + 2; j++) {
                if (i == mainx && j == mainy) continue;
                if (i < 0 || i > 8 || j < 0 || j > 4) continue;
                if((Math.abs(i-mainx)+Math.abs(j-mainy))>2)continue;
                boolean isOccupied = false;
                for (TileUnit item : m_unitPlayerList) {if (i == item.tilex && j == item.tiley && item.intHealth > 0) {isOccupied = true;break;}}
                if(!isOccupied) {for (TileUnit item : m_unitAiList) {if (i == item.tilex && j == item.tiley && item.intHealth > 0) {isOccupied = true;break;}}}
                if (!isOccupied) {//empty
                    //The void can attack a human player
                    allFreePositions.add(new TiltPos(i, j));
                    if(iscanattack(i,j)){
                        possiblePositions.add(new TiltPos(i,j));
                    }
                }
            }
        }
        if (!possiblePositions.isEmpty()) {
            return possiblePositions.get(random.nextInt(possiblePositions.size()));
        }
        if (!allFreePositions.isEmpty()) {
            return allFreePositions.get(random.nextInt(allFreePositions.size()));
        }
        return null;
    }
    public static TiltPos getRoundRandomFreePos(int mainx, int mainy) {
        Random random = new Random();
        List<TiltPos> possiblePositions = new ArrayList<>();

        for (int i = mainx - 1; i <= mainx + 1; i++) {
            for (int j = mainy - 1; j <= mainy + 1; j++) {
                if (i == mainx && j == mainy) continue;
                if (i < 0 || i > 8 || j < 0 || j > 4) continue;
                boolean isOccupied = false;
                for (TileUnit item : m_unitPlayerList) {
                    if (i == item.tilex && j == item.tiley && item.intHealth > 0) {
                        isOccupied = true;
                        break;
                    }
                }
                if(!isOccupied) {
                    for (TileUnit item : m_unitAiList) {
                        if (i == item.tilex && j == item.tiley && item.intHealth > 0) {
                            isOccupied = true;
                            break;
                        }
                    }
                }
                if (!isOccupied) {
                    possiblePositions.add(new TiltPos(i, j));
                }
            }
        }
        if (!possiblePositions.isEmpty()) {
            return possiblePositions.get(random.nextInt(possiblePositions.size()));
        }
        return null;
    }
    public static void highlight_card(ActorRef out, int position, GameState gameState, JsonNode message){
        if(m_currentstate == 0 || m_currentstate == 1 || m_currentstate == 2){ //You can't click on card when you're ready to attack
            if(m_turn == 0){
                int id = getCardNo(m_PlayerCards,position);
                Card c = m_PlayerCards.get(id);
                if(c.getManacost() <= m_humanPlayer.getMana() && c.getManacost() > 0){
                    m_currentstate = 3;
                    m_clickcardid = id;
                }else{
                    m_currentstate = 0;
                }
            }else{
                int id = getCardNo(m_AiCards,position);
                Card c = m_AiCards.get(id);
                if(c.getManacost() <= m_aiPlayer.getMana() && c.getManacost() > 0){
                    m_currentstate = 3;
                    m_clickcardid = id;
                }else{
                    m_currentstate = 0;
                }
            }
            refreshBoard(out);
        }
    }

    public static int  getCardNo(List<Card> a,int position){
        int handPosition = 1;
        for(int i=0;i<a.size();i++){
            Card c = a.get(i);
            if(!c.isCreature()){
                if(handPosition == position){
                    return i;
                }
                handPosition++;
            }
        }
        return -1;
    }
    public static int  getUintNo(ArrayList<TileUnit>  a,int tilex,int tiley){
        for(int i=0;i<a.size();i++){
            TileUnit  t = a.get(i);
            if(t.intHealth <= 0)continue;
            if(t.tilex == tilex && t.tiley == tiley)
                return  i;
        }
        return -1;
    }
    public static boolean enter_walking_state_1(int tilex,int tiley){
        if(m_turn == 0){//player turn
            int  id = getUintNo(m_unitPlayerList,tilex,tiley);
            if(id >= 0){
                TileUnit  t = m_unitPlayerList.get(id);
                if(!t.isMove) {
                    m_currentstate = 1; //Enter walking mode
                    m_clickunitposx = tilex;
                    m_clickunitposy = tiley;
                    return true;
                }
            }
        }else{//ai轮
            int  id = getUintNo(m_unitAiList,tilex,tiley);
            if(id >= 0){
                TileUnit  t = m_unitAiList.get(id);
                if(!t.isMove) {
                    m_currentstate = 1; //Enter walking mode
                    m_clickunitposx = tilex;
                    m_clickunitposy = tiley;
                    return true;
                }
            }
        }
        return false;
    }
    public static void highlight_tile(ActorRef out, int tilex,int tiley, GameState gameState, JsonNode message){
        if(m_currentstate == 0) //noclick
        {
            if(enter_walking_state_1(tilex,tiley)){
                refreshBoard(out);//click hero
                return;
            }
        }else if(m_currentstate == 1){//Click on a Unit and get ready to walk
            if(enter_walking_state_1(tilex,tiley)){
                refreshBoard(out);//click hero
                return ;
            }
            if(isrun(tilex,tiley,m_clickunitposx,m_clickunitposy))  //If you hit the point where you want to go
            {
                TileUnit t = null;
                if(m_turn == 0){
                    t = getUint(m_unitPlayerList,m_clickunitposx,m_clickunitposy);
                    System.out.println("1----=" + m_clickunitposx + ":" + m_clickunitposy);
                }else{
                    t = getUint(m_unitAiList,m_clickunitposx,m_clickunitposy);
                    System.out.println("2----=" + m_clickunitposx + ":" + m_clickunitposy);
                }
                if(t != null){
                    BasicCommands.addPlayer1Notification(out, "Moving", 3);
                    System.out.println("move:"+m_clickunitposx+":"+m_clickunitposy+" to "+tilex+":"+tiley + "--" + t.id);
                    Tile tile = BasicObjectBuilders.loadTile(tilex, tiley);
                    BasicCommands.moveUnitToTile(out, t.cur_unit, tile);
                    //There are enemies around to enter attack mode, otherwise enter waiting mode
                    if(iscanattack(tilex,tiley)){
                        m_currentstate = 2;  //Enter the attack state
                        m_clickunitposx = tilex;
                        m_clickunitposy = tiley;
                    }else{
                        m_currentstate = 0;
                    }
                    t.tilex = tilex;
                    t.tiley = tiley;
                    t.isMove = true;
                    try {Thread.sleep(2500);} catch (InterruptedException e) {e.printStackTrace();}
                    refreshBoard(out);
                    return;
                }
            }
            m_currentstate = 0;//Click on the other cell to enter the original state
        }else if(m_currentstate == 2){//Clicked the walk, ready to attack
            if(m_turn == 0){
                int id = getUintNo(m_unitAiList,tilex,tiley);
                if(id >= 0){
                    int myid = getUintNo(m_unitPlayerList,m_clickunitposx,m_clickunitposy);
                    if(myid >= 0){//attack
                        TileUnit t = m_unitPlayerList.get(myid);
                        BasicCommands.addPlayer1Notification(out, "Attack", 3);
                        try {Thread.sleep(BasicCommands.playUnitAnimation(out, t.cur_unit, UnitAnimationType.attack));} catch (InterruptedException e) {e.printStackTrace();}
                        TileUnit t1 = m_unitAiList.get(id);
                        t1.intHealth -= t.intAttack;
                        if(t1.intHealth <= 0){//Delete animation
                            t1.intHealth = 0;
                            try {Thread.sleep(BasicCommands.playUnitAnimation(out, t1.cur_unit, UnitAnimationType.death));} catch (InterruptedException e) {e.printStackTrace();}
                            BasicCommands.deleteUnit(out, t1.cur_unit);
                            if(t1.isPlayer == 1){
                                gameState.gameInitalised = false;
                                gameState.something = false;
                                BasicCommands.addPlayer1Notification(out, "You Win!Game Over!", 10);
                            }else {
                                BasicCommands.addPlayer1Notification(out, "Heroic death!", 3);
                            }
                        }else{
                            BasicCommands.setUnitHealth(out, t1.cur_unit, t1.intHealth);
                        }
                        //If it's the main character, update health
                        if(t1.isPlayer == 1){
                            if(m_turn == 0){
                                m_humanPlayer.setHealth(t1.intHealth);
                            }else{
                                m_aiPlayer.setHealth(t1.intHealth);
                            }
                        }
                    }
                    m_currentstate = 0;
                    refreshBoard(out);
                }
            }else{
                int id = getUintNo(m_unitPlayerList,tilex,tiley);
                if(id >= 0){
                    int myid = getUintNo(m_unitAiList,m_clickunitposx,m_clickunitposy);
                    if(myid >= 0){//attack
                        TileUnit t = m_unitAiList.get(myid);
                        try {Thread.sleep(BasicCommands.playUnitAnimation(out, t.cur_unit, UnitAnimationType.attack));} catch (InterruptedException e) {e.printStackTrace();}
                        TileUnit t1 = m_unitPlayerList.get(id);
                        t1.intHealth -= t.intAttack;
                        if(t1.intHealth <= 0){//Delete animation
                            t1.intHealth = 0;
                            try {Thread.sleep(BasicCommands.playUnitAnimation(out, t1.cur_unit, UnitAnimationType.death));} catch (InterruptedException e) {e.printStackTrace();}
                            BasicCommands.deleteUnit(out, t1.cur_unit);
                            if(t1.isPlayer == 1){
                                gameState.gameInitalised = false;
                                gameState.something = false;
                                BasicCommands.addPlayer1Notification(out, "You Loss!Game Over!", 10);
                            }
                        }else{
                            BasicCommands.setUnitHealth(out, t1.cur_unit, t1.intHealth);
                        }
                        //If it's the main character, update health
                        if(t1.isPlayer == 1){
                            if(m_turn == 0){
                                m_humanPlayer.setHealth(t1.intHealth);
                            }else{
                                m_aiPlayer.setHealth(t1.intHealth);
                            }
                        }
                    }
                    m_currentstate = 0;
                    refreshBoard(out);
                }
            }
        }else if(m_currentstate == 3){//Clicked card, ready to summon
            if(enter_walking_state_1(tilex,tiley)){//Clicked hero
                refreshBoard(out);
                return ;
            }
            //summon units
            if(m_turn == 0) {
                TileUnit   my = m_unitPlayerList.get(0);
                if(isround(tilex,tiley,my.tilex,my.tiley)) {
                    Card c = m_PlayerCards.get(m_clickcardid);
                    if(c != null){
                        TileUnit unit = new TileUnit();
                        unit.id = m_unitPlayerList.size();unit.isPlayer=0;
                        unit.tilex=tilex;unit.tiley=tiley;
                        unit.intAttack = 2;unit.intHealth=c.getManacost();
                        c.setIsCreature(true);
                        BasicCommands.addPlayer1Notification(out, "Calling", 3);
                        unit.cur_unit = BasicObjectBuilders.loadUnit(c.getUnitConfig(), unit.id, Unit.class);
                        unit.isShow = false;
                        unit.isMove = false;
                        int curMana = m_humanPlayer.getMana()-c.getManacost();
                        if (curMana<0){curMana=0;}
                        m_humanPlayer.setMana(curMana);
                        m_unitPlayerList.add(unit);
                    }
                }
            }else{
                TileUnit   my = m_unitAiList.get(0);
                if(isround(tilex,tiley,my.tilex,my.tiley)) {
                    Card c = m_AiCards.get(m_clickcardid);
                    if(c != null){
                        TileUnit unit1 = new TileUnit();
                        unit1.id = m_unitAiList.size();unit1.isPlayer=0;
                        unit1.tilex=tilex;unit1.tiley=tiley;
                        unit1.intAttack = 2;unit1.intHealth=c.getManacost();
                        c.setIsCreature(true);
                        BasicCommands.addPlayer1Notification(out, "Calling", 3);
                        unit1.cur_unit = BasicObjectBuilders.loadUnit(c.getUnitConfig(), unit1.id, Unit.class);
                        unit1.isShow = false;
                        unit1.isMove = false;
                        int curMana = m_aiPlayer.getMana()-c.getManacost();
                        if (curMana<0){curMana=0;}
                        m_aiPlayer.setMana(curMana);
                        m_unitAiList.add(unit1);
                        System.out.println("Ai call");
                    }
                }
            }

            m_currentstate = 0;
            refreshBoard(out);
        }


    }

    //Gets the Unit at the specified location
    private static TileUnit getUint(ArrayList<TileUnit> a,int  tilex,int tiley){
        for (TileUnit item : a) {
            if(item.tilex == tilex && item.tiley == tiley && item.intHealth > 0){
                return item;
            }
        }

/*        for (TileUnit item : m_unitPlayerList) {
            if(item.tilex == tilex && item.tiley == tiley && item.intHealth > 0){
                return item;
            }
        }
        for (TileUnit item : m_unitAiList) {
            if(item.tilex == tilex && item.tiley == tiley  && item.intHealth > 0){
                return item;
            }
        }*/
        return null;
    }

    //Determines whether the specified location can be reached
    private static boolean isrun(int i,int j,int posx,int posy){
        if(i==posx && j==posy)return false;
        if((Math.abs(i-posx) + Math.abs(j-posy)) <= 2)
        {
            for (TileUnit item : m_unitPlayerList) {
                if(item.intHealth <= 0)continue;
                if(item.tilex == i && item.tiley == j)return false;
            }
            for (TileUnit item : m_unitAiList) {
                if(item.intHealth <= 0)continue;
                if(item.tilex == i && item.tiley == j)return false;
            }
            return true;
        }
        return false;
    }
    //Determine whether the point is around the target point
    private static boolean isround(int i,int j,int posx,int posy){
        if(i==posx && j==posy)return false;
        if(i >= (posx-1) && i<=(posx+1) && j >= (posy-1) && j <= (posy+1))
        {
            for (TileUnit item : m_unitPlayerList) {
                if(item.intHealth <= 0)continue;
                if(item.tilex == i && item.tiley == j)return false;
            }
            for (TileUnit item : m_unitAiList) {
                if(item.intHealth <= 0)continue;
                if(item.tilex == i && item.tiley == j)return false;
            }
            return true;
        }
        return false;
    }

    //Determine if there are enemy forces around the point
    private static boolean iscanattack(int tilex,int tiley){
        if(m_turn == 0){
            for (TileUnit item : m_unitAiList) {
                if(item.intHealth <= 0)continue;
                if(item.tilex >= (tilex-1) && item.tilex <= (tilex+1)
                        && item.tiley >= (tiley-1) && item.tiley <= (tiley+1))
                    return true;
            }
        }else{
            for (TileUnit item : m_unitPlayerList) {
                if(item.intHealth <= 0 )continue;
                if(item.tilex >= (tilex-1) && item.tilex <= (tilex+1)
                        && item.tiley >= (tiley-1) && item.tiley <= (tiley+1))
                    return true;
            }
        }
        return false;
    }

    //This point can attack point
    private static boolean isattack(int i,int j,int posx,int posy){
        if(i==posx && j==posy)return false;
        if(i >= (posx-1) && i<=(posx+1) && j >= (posy-1) && j <= (posy+1))
        {
            if(m_turn == 0){
                for (TileUnit item : m_unitAiList) {
                    if(item.intHealth <= 0)continue;
                    if(item.tilex == i && item.tiley == j)return true;
                }
            }else{
                for (TileUnit item : m_unitPlayerList) {
                    if(item.intHealth <= 0)continue;
                    if(item.tilex == i && item.tiley == j)return true;
                }
            }
        }
        return false;
    }
    private static void refreshBoard(ActorRef out) {
        // Create 9x5 board
        BasicCommands.setPlayer2Health(out, m_humanPlayer);
        BasicCommands.setPlayer1Health(out, m_aiPlayer);

        BasicCommands.setPlayer1Mana(out, m_humanPlayer);
        BasicCommands.setPlayer2Mana(out, m_aiPlayer);

        //draw cards
        if(m_turn == 0)
        {
            int handPosition = 1;
            for(int i=0;i<m_PlayerCards.size();i++){
                Card c = m_PlayerCards.get(i);
                if(!c.isCreature()){
                    if(m_currentstate == 3 && m_clickcardid == i){
                        BasicCommands.drawCard(out, c, handPosition, 1);
                    }else{
                        BasicCommands.drawCard(out, c, handPosition, 0);
                    }
                    handPosition++;
                }
                if (handPosition>6) break;
            }

            while(handPosition <= 6){
                BasicCommands.drawCard(out, null, handPosition, 0);
                handPosition++;
            }
        }else{
            int handPosition = 1;
            for(int i=0;i<m_AiCards.size();i++){
                Card c = m_AiCards.get(i);
                if(!c.isCreature()){
                    if(m_currentstate == 3 && m_clickcardid == i){
                        BasicCommands.drawCard(out, c, handPosition, 1);
                    }else{
                        BasicCommands.drawCard(out, c, handPosition, 0);
                    }
                    handPosition++;
                }
                if (handPosition>6) break;
            }
            while(handPosition <= 6){
                BasicCommands.drawCard(out, null, handPosition, 0);
                handPosition++;
            }
        }

        int  tix,tiy;
        if(m_turn == 0)
        {
            TileUnit  t = m_unitPlayerList.get(0);
            tix = t.tilex;tiy=t.tiley;
        }
        else {
            TileUnit  t = m_unitAiList.get(0);
            tix = t.tilex;tiy=t.tiley;
        }
        //draw tile
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 5; j++) {
                Tile tmptile = BasicObjectBuilders.loadTile(i, j);
                if(m_currentstate == 3 && isround(i,j,tix,tiy)){// in the stage of selecting cards, the position not occupied by the main player is displayed
                    BasicCommands.drawTile(out, tmptile, 1);
                }else if(m_currentstate == 1 && isrun(i,j,m_clickunitposx,m_clickunitposy)) {//In the select unit phase, shows where you can go
                    BasicCommands.drawTile(out, tmptile, 1);
                }else if(m_currentstate == 2 && isattack(i,j,m_clickunitposx,m_clickunitposy)){ //In the stage of selecting the attack, select the hero to attack
                    BasicCommands.drawTile(out, tmptile, 2);
                }else{
                    BasicCommands.drawTile(out, tmptile, 0);
                }

            }
        }

        Iterator<TileUnit> iterator = m_unitPlayerList.iterator();
        while (iterator.hasNext()) {
            TileUnit titlunit = iterator.next();
            if(titlunit.intHealth > 0 && !titlunit.isShow) {
                Tile tile = BasicObjectBuilders.loadTile(titlunit.tilex, titlunit.tiley);
                titlunit.cur_unit.setPositionByTile(tile);
                BasicCommands.drawUnit(out,null,tile);
                BasicCommands.drawUnit(out, titlunit.cur_unit, tile);
                try {Thread.sleep(100);} catch (InterruptedException e) {e.printStackTrace();}

                BasicCommands.setUnitAttack(out, titlunit.cur_unit, titlunit.intAttack);
                BasicCommands.setUnitHealth(out, titlunit.cur_unit, titlunit.intHealth);
                titlunit.isShow = true;
            }
        }
        iterator = m_unitAiList.iterator();
        while (iterator.hasNext()) {
            TileUnit titlunit = iterator.next();
            if(titlunit.intHealth > 0 && !titlunit.isShow) {
                Tile tile = BasicObjectBuilders.loadTile(titlunit.tilex, titlunit.tiley);
                titlunit.cur_unit.setPositionByTile(tile);
                BasicCommands.drawUnit(out, titlunit.cur_unit, tile);
                try {Thread.sleep(100);} catch (InterruptedException e) {e.printStackTrace();}

                BasicCommands.setUnitAttack(out, titlunit.cur_unit, titlunit.intAttack);
                BasicCommands.setUnitHealth(out, titlunit.cur_unit, titlunit.intHealth);
                titlunit.isShow = true;
            }
        }

    }
}
