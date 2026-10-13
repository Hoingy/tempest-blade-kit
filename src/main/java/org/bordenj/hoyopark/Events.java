package org.bordenj.hoyopark;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.util.TriState;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Events implements Listener {

    enum YasState {DASHING,IDLE,CASTING}

    private final Main plugin;
    public Events(Main plugin){
        this.plugin=plugin;
    }

    HashMap<Player, Integer> Qstacks = new HashMap<>();
    HashMap<Player, Integer> Flow = new HashMap<>();
    HashMap<Player,YasState> States =  new HashMap<>();
    ArrayList<LivingEntity> Ecooldown = new ArrayList<>();
    ArrayList<LivingEntity> Airborne = new ArrayList<>();

    public void addStates(Player p){
        States.put(p,YasState.IDLE);
    }

    public void cleanup(){
        Qstacks.clear();
        Flow.clear();
        States.clear();
        Ecooldown.clear();
        Airborne.clear();
    }

    public void knockUp(LivingEntity target, int airTicks) {
        if (airTicks <= 0) return;


        double g = 0.08;
        AttributeInstance gravity = target.getAttribute(Attribute.GRAVITY);
        if (gravity != null) {
            g = gravity.getValue();
        }

        Airborne.add(target);
        double vy = -49 * g + 0.98 * g * airTicks / (1 - Math.pow(0.98, airTicks));

        Vector v = target.getVelocity();
        v.setY(vy);
        target.setVelocity(v);

        new BukkitRunnable() {
            @Override
            public void run() {
                Airborne.remove(target);
            }
        }.runTaskLater(plugin, airTicks+10);
    }

    public void drawLine(Location point1, Location point2, double space, Particle.DustOptions dust, double ox, double oy, double oz) {
        World world = point1.getWorld();
        double distance = point1.distance(point2);
        Vector p1 = point1.toVector();
        Vector p2 = point2.toVector();
        Vector vector = p2.clone().subtract(p1).normalize().multiply(space);
        double length = 0;
        for (; length < distance; p1.add(vector)) {
            world.spawnParticle(Particle.DUST, p1.getX(), p1.getY(), p1.getZ(), 3,ox,oy,oz, dust);
            length += space;
        }
    }
    public void drawCircle(Location center, double radius, int points,Particle particle) {
        World world = center.getWorld();
        if (world == null || points <= 0 || radius < 0) return;

        double cx = center.getX();
        double y  = center.getY();
        double cz = center.getZ();

        for (int i = 0; i < points; i++) {
            double theta = (2.0 * Math.PI * i) / points;
            double x = cx + radius * Math.cos(theta);
            double z = cz + radius * Math.sin(theta);
            //world.spawnParticle(Particle.ENCHANTED_HIT, x, y, z, 1, 0.05, 0.05, 0.05, 0);
            world.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
        }
    }
    public void decayRing(LivingEntity target, double radius, int durationTicks,int points, double yOffset) {
        Ecooldown.add(target);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= durationTicks || !target.isValid()) {
                    Ecooldown.remove(target);
                    cancel();
                    return;
                }

                int shown = (int) Math.round(points * (1.0 - (double) t / durationTicks));

                Location c = target.getLocation().add(0, yOffset, 0);
                World w = c.getWorld();
                double startAngle = -Math.PI / 2;

                for (int i = 0; i < shown; i++) {
                    double theta = startAngle + (2 * Math.PI * i) / points;
                    w.spawnParticle(Particle.ENCHANTED_HIT,
                            c.getX() + radius * Math.cos(theta),
                            c.getY(),
                            c.getZ() + radius * Math.sin(theta),
                            1, 0, 0, 0, 0);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    public void launchTornado(Player p) {
        ArmorStand tornado = (ArmorStand) p.getWorld().spawnEntity(p.getLocation(), EntityType.ARMOR_STAND);

        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_WHIRL,3,2f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST,0.3f,0.5f);
        tornado.setArms(true);
        //tornado.setInvulnerable(true);
        tornado.setInvisible(true);
        tornado.setCollidable(false);
        tornado.setFrictionState(TriState.FALSE);

        Location loc = p.getLocation();
        Vector forward = p.getLocation().getDirection();
        forward.setY(0);
        forward.normalize();
        Location forwardloc = loc.clone().add(forward);
        forwardloc.setY(loc.getY());

        tornado.setVelocity(forwardloc.toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.5));

        new BukkitRunnable() {
            int x = 0;
            @Override
            public void run() {
                if(x>20){
                    tornado.remove();
                    cancel();
                }
                x++;
                for(Entity e:p.getWorld().getNearbyEntities(tornado.getBoundingBox())){
                    if(e instanceof LivingEntity&&e!=tornado&&e!=p){
                        //apply knock up
                        ((LivingEntity) e).damage(3,p);
                        knockUp((LivingEntity) e,20);

                    }
                }

                new BukkitRunnable(){
                    @Override
                    public void run() {
                        Location wind = tornado.getLocation();
                        wind.setY(wind.getY()+0.7);
                        for(double x=0;x<=3;x+=0.2){
                            drawCircle(new Location(p.getWorld(),wind.x(),wind.y()+x,wind.z()),(x+.5)*0.3, (int) ((x+.5)*10),Particle.CRIT);
                        }
                    }
                }.runTaskLater(plugin,1);
            }
        }.runTaskTimer(plugin, 0, 1);

    }

    public void SteelTempest(Player p){
        if(p.getCooldown(Material.IRON_SWORD)==0){
            p.setCooldown(Material.IRON_SWORD,30);
            if(Qstacks.containsKey(p)&&Qstacks.get(p)>=2){
                Qstacks.remove(p);
                launchTornado(p);
                return;
            }
            Objects.requireNonNull(p.getAttribute(Attribute.JUMP_STRENGTH)).setBaseValue(0.42f);
            Objects.requireNonNull(p.getAttribute(Attribute.JUMP_STRENGTH)).setBaseValue(0f);
            p.getInventory().getItemInMainHand().setAmount(0);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP,1,3);
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_THROW,1,2);

            //Vector forwardDir = p.getLocation().getDirection();
            //Vector toRight = forwardDir.crossProduct(new Vector(0, 1, 0)).multiply(0.2);
            //Location rightLoc = p.getLocation().add(toRight);

            /*ArmorStand steeltempest = (ArmorStand) p.getWorld().spawnEntity(rightLoc, EntityType.ARMOR_STAND);

            steeltempest.setArms(true);
            steeltempest.setItem(EquipmentSlot.HAND,new ItemStack(Material.IRON_SWORD));
            steeltempest.setRightArmPose(new EulerAngle(Math.toRadians(351),0,Math.toRadians(270)));
            steeltempest.setInvulnerable(true);
            steeltempest.setInvisible(true);*/

            Location loc = p.getLocation();
            Vector forward = p.getLocation().getDirection();
            forward.normalize().multiply(3);
            Location forwardloc = loc.clone().add(forward);

            forwardloc.setY(forwardloc.getY() + 1);
            loc.setY(loc.getY() + 1);

            drawLine(loc,forwardloc,0.2, new Particle.DustOptions(Color.SILVER,1),0,0,0);

            Vector start = loc.toVector();
            //Vector dir = loc.getDirection();

            BoundingBox stab = BoundingBox.of(start, forwardloc.toVector()).expand(0.5);

            Collection<Entity> targets = p.getWorld().getNearbyEntities(
                    stab,
                    en -> en instanceof LivingEntity && !en.equals(p)
            );

            if (!targets.isEmpty()) {
                Qstacks.merge(p, 1, Integer::sum);
                if(p.getInventory().contains(Material.BREEZE_ROD)){
                    p.getInventory().addItem(new ItemStack(Material.BREEZE_ROD));
                    p.setCooldown(Material.BREEZE_ROD,6*20);
                }else{
                    p.getInventory().setItem(7,new ItemStack(Material.BREEZE_ROD));
                    p.setCooldown(Material.BREEZE_ROD,6*20);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if(!p.isValid() || p.getCooldown(Material.BREEZE_ROD)==0 ||!Qstacks.containsKey(p)){
                                Qstacks.remove(p);
                                p.getInventory().removeItem(new ItemStack(Material.BREEZE_ROD,2));
                                cancel();
                            }
                            p.getWorld().spawnParticle(Particle.DUST,p.getLocation(),8,0.5,0.1,0.5,new Particle.DustOptions(Color.WHITE,0.8f));
                        }
                    }.runTaskTimer(plugin,0,1);
                }

                for (Entity en : targets) {
                    ((LivingEntity) en).damage(3, p);
                }
            }

            new BukkitRunnable(){
                @Override
                public void run() {
                    Objects.requireNonNull(p.getAttribute(Attribute.JUMP_STRENGTH)).setBaseValue(0.42f);
                    p.getInventory().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                    this.cancel();
                }
            }.runTaskLater(plugin,5);
        }
    }

    public void CircleTempest(Player p){
        if(p.getCooldown(Material.IRON_SWORD)==0){
            p.setCooldown(Material.IRON_SWORD,30);

            Location loc = p.getLocation();
            Vector forward = p.getLocation().getDirection();
            forward.setY(0);
            forward.normalize();
            Location forwardloc = loc.clone().add(forward);
            forwardloc.setY(loc.getY());

            Vector toRight = forward.clone().crossProduct(new Vector(0, 1, 0)).multiply(2);
            Location rightCorner = p.getLocation().add(toRight);
            rightCorner.add(forward.clone().multiply(2));
            Vector toLeft = forward.clone().crossProduct(new Vector(0, -1, 0)).multiply(2);
            Location leftCorner = p.getLocation().add(toLeft);
            leftCorner.add(forward.clone().multiply(-2));

            BoundingBox stab = BoundingBox.of(leftCorner, rightCorner).expand(0.5);

            if(Qstacks.containsKey(p)&&Qstacks.get(p)>=2){
                Qstacks.remove(p);
                //circle knock
                for(double x =1;x<=2;x+=0.2){
                    if(x>1.7){
                        drawCircle(p.getLocation().clone().add(0,1,0),x, (int) (20+x*50),Particle.ENCHANTED_HIT);
                    }
                    else{
                        drawCircle(p.getLocation().clone().add(0,1,0),x, (int) (20+x*50),Particle.CRIT);
                    }
                }

                Collection<Entity> targets = p.getWorld().getNearbyEntities(
                        stab,
                        en -> en instanceof LivingEntity && !en.equals(p)
                );

                if (!targets.isEmpty()) {
                    for (Entity en : targets) {
                        ((LivingEntity) en).damage(3, p);
                        knockUp((LivingEntity) en,20);
                    }
                }
                return;
            }

            Objects.requireNonNull(p.getAttribute(Attribute.JUMP_STRENGTH)).setBaseValue(0.42f);
            Objects.requireNonNull(p.getAttribute(Attribute.JUMP_STRENGTH)).setBaseValue(0f);
            p.getInventory().getItemInMainHand().setAmount(0);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP,1,3);
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_THROW,1,2);





            for(double x =1;x<=2;x+=0.2){
                drawCircle(p.getLocation().clone().add(0,1,0),x, (int) (20+x*50),Particle.CRIT);
            }

            Collection<Entity> targets = p.getWorld().getNearbyEntities(
                    stab,
                    en -> en instanceof LivingEntity && !en.equals(p)
            );

            if (!targets.isEmpty()) {
                Qstacks.merge(p, 1, Integer::sum);

                if(p.getInventory().contains(Material.BREEZE_ROD)){
                    p.getInventory().addItem(new ItemStack(Material.BREEZE_ROD));
                    p.setCooldown(Material.BREEZE_ROD,6*20);
                }else{
                    p.getInventory().setItem(7,new ItemStack(Material.BREEZE_ROD));
                    p.setCooldown(Material.BREEZE_ROD,6*20);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if(!p.isValid() || p.getCooldown(Material.BREEZE_ROD)==0 || !Qstacks.containsKey(p)){
                                Qstacks.remove(p);
                                p.getInventory().removeItem(new ItemStack(Material.BREEZE_ROD,2));
                                cancel();
                            }
                            p.getWorld().spawnParticle(Particle.DUST,p.getLocation(),8,0.5,0.1,0.5,new Particle.DustOptions(Color.WHITE,0.8f));
                        }
                    }.runTaskTimer(plugin,0,1);
                }

                for (Entity en : targets) {
                    ((LivingEntity) en).damage(3, p);
                }
            }

            new BukkitRunnable(){
                @Override
                public void run() {
                    //steeltempest.remove();
                    Objects.requireNonNull(p.getAttribute(Attribute.JUMP_STRENGTH)).setBaseValue(0.42f);
                    p.getInventory().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                    this.cancel();
                }
            }.runTaskLater(plugin,5);
        }
    }

    public void WindWall(Player p){
        ArmorStand wall = (ArmorStand) p.getWorld().spawnEntity(p.getLocation(), EntityType.ARMOR_STAND);
        wall.setInvisible(true);
        wall.setArms(true);
        wall.setInvulnerable(true);
        wall.setCollidable(false);
        wall.setFrictionState(TriState.FALSE);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_WHIRL,3,2f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST,0.3f,0.5f);

        Location loc = p.getLocation();
        Vector forward = p.getLocation().getDirection();
        forward.setY(0);
        forward.normalize();
        Location forwardloc = loc.clone().add(forward);
        forwardloc.setY(loc.getY());

        wall.getLocation().setDirection(p.getLocation().getDirection());
        wall.setVelocity(forwardloc.toVector().subtract(p.getLocation().toVector()).normalize().multiply(0.35));



        new BukkitRunnable(){
            int x = 0;
            @Override
            public void run() {
                if(x>4*20){
                    wall.remove();
                    cancel();
                }
                x++;

                Vector toRight = forward.clone().crossProduct(new Vector(0, 1, 0)).multiply(2.5);
                Location rightLoc = wall.getLocation().add(toRight);
                Vector toLeft = forward.clone().crossProduct(new Vector(0, -1, 0)).multiply(2.5);
                Location leftLoc = wall.getLocation().add(toLeft);
                leftLoc.setY(leftLoc.getY() + 3);

                BoundingBox barrier = BoundingBox.of(rightLoc,leftLoc).expand(1);
                for(Entity e:p.getWorld().getNearbyEntities(barrier)){
                    if(e instanceof Projectile){
                        e.remove();
                    }
                }
                leftLoc.setY(leftLoc.getY() - 3);
                drawLine(rightLoc,leftLoc,0.2,new Particle.DustOptions(Color.GRAY,1),0,0,0);

                drawLine(leftLoc,leftLoc.clone().add(0,2,0),0.2,new Particle.DustOptions(Color.WHITE,1),0,0,0);
                drawLine(rightLoc,rightLoc.clone().add(0,2,0),0.2,new Particle.DustOptions(Color.WHITE,1),0,0,0);

                double height = 2;
                Vector span = leftLoc.toVector().subtract(rightLoc.toVector());

                for (int i = 0; i < 10; i++) {
                    double t = ThreadLocalRandom.current().nextDouble();
                    double h = ThreadLocalRandom.current().nextDouble() * height;

                    Location pt = rightLoc.clone()
                            .add(span.clone().multiply(t))
                            .add(0, h, 0);

                    p.getWorld().spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, new Particle.DustOptions(Color.WHITE,0.6f));
                }

            }
        }.runTaskTimer(plugin,0,1);

        new BukkitRunnable() {
            @Override
            public void run() {
                wall.setFrictionState(TriState.TRUE);

            }
        }.runTaskLater(plugin,15);
    }

    public void LastBreath(Player p,LivingEntity target) {

        Location hover = target.getLocation();
        hover.setY(p.getLocation().getY()+5);
        target.teleport(hover);
        target.setGravity(false);
        target.setAI(false);
        target.setVelocity(new Vector(0,0,0));

        p.setGravity(false);
        Location spot = hover.add(target.getLocation().getDirection().normalize().multiply(-3));
        p.teleport(spot);
        p.setVelocity(new Vector(0,0,0));

        Vector forward = target.getLocation().toVector().subtract(spot.toVector()).setY(0).normalize();
        Vector right = forward.clone().rotateAroundY(-Math.PI / 2);

        Location rightLoc = spot.clone().add(right.clone().multiply(2));
        Location leftLoc  = spot.clone().subtract(right.clone().multiply(2));

        rightLoc.setY(rightLoc.getY() -1);
        leftLoc.setY(leftLoc.getY() -1);

        Location rightTop = rightLoc.clone().add(0,3,0);
        Location leftTop = leftLoc.clone().add(0,3,0);

        for(Entity e:target.getNearbyEntities(3,1,3)){
            if(e instanceof LivingEntity en && Airborne.contains(en)){
                Location hover2 = en.getLocation();
                hover2.setY(p.getLocation().getY());
                en.teleport(hover2);
                en.setGravity(false);
                en.setAI(false);
                en.setVelocity(new Vector(0,0,0));

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        en.setGravity(true);
                        en.setAI(true);
                        en.setVelocity(new Vector(0,-1,0));

                        en.damage(6,p);

                    }
                }.runTaskLater(plugin,20);
            }
        }


        new BukkitRunnable() {
            @Override
            public void run() {
                ArmorStand hit = (ArmorStand) p.getWorld().spawnEntity(rightLoc,EntityType.ARMOR_STAND);
                hit.getLocation().setDirection(hit.getLocation().toVector().subtract(target.getLocation().toVector()));
                hit.setInvisible(true);
                hit.setItem(EquipmentSlot.OFF_HAND,new ItemStack(Material.IRON_SWORD));
                hit.setLeftArmPose(new EulerAngle(0,0,Math.toRadians(60)));
                Vector fly = hit.getLocation().toVector().subtract(leftTop.toVector()).normalize().multiply(-2);
                hit.setVelocity(fly);
                p.getWorld().spawnParticle(Particle.CRIT,target.getLocation(),10,0,0,0,1);
                p.getWorld().playSound(p.getLocation(),Sound.ITEM_SPEAR_HIT,0.5f,1f);

                target.damage(2,p);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        hit.remove();
                    }
                }.runTaskLater(plugin,6);
            }
        }.runTaskLater(plugin,5);

        new BukkitRunnable() {
            @Override
            public void run() {
                ArmorStand hit = (ArmorStand) p.getWorld().spawnEntity(leftLoc,EntityType.ARMOR_STAND);
                hit.getLocation().setDirection(hit.getLocation().toVector().subtract(target.getLocation().toVector()));
                hit.setInvisible(true);
                hit.setItem(EquipmentSlot.HAND,new ItemStack(Material.IRON_SWORD));
                hit.setRightArmPose(new EulerAngle(0,0,Math.toRadians(300)));
                Vector fly = hit.getLocation().toVector().subtract(rightTop.toVector()).normalize().multiply(-2);
                hit.setVelocity(fly);
                p.getWorld().spawnParticle(Particle.CRIT,target.getLocation(),10,0,0,0,1);
                p.getWorld().playSound(p.getLocation(),Sound.ITEM_SPEAR_HIT,0.5f,1f);

                target.damage(2,p);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        hit.remove();
                    }
                }.runTaskLater(plugin,6);
            }
        }.runTaskLater(plugin,10);


        new BukkitRunnable() {
            @Override
            public void run() {
                ArmorStand hit = (ArmorStand) p.getWorld().spawnEntity(spot.clone().subtract(right.clone().multiply(0.5)),EntityType.ARMOR_STAND);
                hit.getLocation().setDirection(hit.getLocation().toVector().subtract(target.getLocation().toVector()));
                hit.setInvisible(true);
                hit.setItem(EquipmentSlot.HAND,new ItemStack(Material.IRON_SWORD));
                hit.setVelocity(new Vector(0,-1.2,0));

                target.setGravity(true);
                target.setAI(true);
                target.setVelocity(new Vector(0,-1,0));

                p.setGravity(true);
                p.setVelocity(new Vector(0,-1,0));

                p.getWorld().spawnParticle(Particle.CRIT,target.getLocation(),10,0,0,0,1);

                target.damage(2,p);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        hit.remove();
                    }
                }.runTaskLater(plugin,8);

            }
        }.runTaskLater(plugin,20);

        new BukkitRunnable() {
            @Override
            public void run() {
                p.getWorld().playSound(p.getLocation(),Sound.ITEM_MACE_SMASH_GROUND_HEAVY,0.5f,1f);
                p.getWorld().spawnParticle(Particle.GUST_EMITTER_LARGE,target.getLocation(),1,0,0,0);
            }
        }.runTaskLater(plugin,23);
    }

    @EventHandler
    public void PlayerClick(PlayerInteractEvent e){
        Player p = e.getPlayer();
        if(e.getAction().equals(Action.RIGHT_CLICK_AIR)||e.getAction().equals(Action.RIGHT_CLICK_BLOCK)){
            if(p.getInventory().getItemInMainHand().getType()== Material.IRON_SWORD&&p.getLocation().clone().subtract(0,1,0).getBlock().getType()!=Material.AIR){
                e.setCancelled(true);
                if(States.get(p)==YasState.DASHING){
                    //circle slash
                    CircleTempest(p);
                }else if(States.get(p)==YasState.IDLE){
                    SteelTempest(p);
                }else if(States.get(p)==YasState.CASTING){
                    p.sendActionBar(Component.text("Can't cast this right now"));
                }
            }

        }
        /*if(e.getAction().equals(Action.LEFT_CLICK_BLOCK)||e.getAction().equals(Action.LEFT_CLICK_AIR)){
            //RANGED AUTO ATTACKS
            Set<Material> set = new HashSet<Material>();
            set.add(Material.AIR);
            Location look = p.getTargetBlock(set,50).getLocation();
            Location source =p.getLocation().clone().add(0,1.1,0);

            World world = source.getWorld();
            double distance = source.distance(look);
            Vector p1 = source.toVector();
            Vector p2 = look.toVector();
            Vector vector = p2.clone().subtract(p1).normalize().multiply(0.5);
            double length = 0;
            for (; length < distance; p1.add(vector)) {
                Location current = new Location(p.getWorld(),p1.getX(),p1.getY(),p1.getZ());
                //world.spawnParticle(Particle.CRIT, current, 0);
                for(Entity en:p.getNearbyEntities(12,12,12)){
                    if(en instanceof LivingEntity){
                        if(en.getLocation().clone().add(0,1,0).distance(current)<1.5){
                            //do effect/damage
                            new BukkitRunnable(){
                                Location shot = p.getLocation().clone().add(0,1.1,0);
                                @Override
                                public void run() {
                                    Location target = en.getLocation().clone().add(0,1,0);
                                    shot = shot.add(target.toVector().subtract(shot.toVector()).normalize().multiply(1.2));
                                    p.getWorld().spawnParticle(Particle.END_ROD,shot,6,0.1,0.1,0.1,0);

                                    if(shot.distance(target)<0.5){
                                        this.cancel();
                                        ((LivingEntity) en).damage(4);
                                    }
                                }
                            }.runTaskTimer(plugin,0,1);
                        }

                    }
                }
                length += 0.3;
            }
        }*/
    }

    @EventHandler
    public void EntityInteract(PlayerInteractEntityEvent e){
        if(e.getRightClicked() instanceof LivingEntity){
            Player p = e.getPlayer();
            if(p.getInventory().getItemInMainHand().getType()== Material.IRON_SWORD&&p.getLocation().clone().subtract(0,1,0).getBlock().getType()!=Material.AIR){
                e.setCancelled(true);
                if(States.get(p)==YasState.DASHING){
                    //circle slash
                    CircleTempest(p);
                }else if(States.get(p)==YasState.IDLE){
                    SteelTempest(p);
                }else if(States.get(p)==YasState.CASTING){
                    p.sendActionBar(Component.text("Can't cast this right now"));
                }
            }
        }
    }

    @EventHandler
    public void PlayerSwapHands(PlayerSwapHandItemsEvent e){
        Player p = e.getPlayer();
        if(p.getInventory().getItemInMainHand().getType()==Material.IRON_SWORD){
            e.setCancelled(true);
            WindWall(p);
        }
    }

    @EventHandler
    public void PlayerDropItem(PlayerDropItemEvent e){
        Player p = e.getPlayer();
        if(e.getItemDrop().getItemStack().getType()==Material.IRON_SWORD){
            e.setCancelled(true);

            LivingEntity target = null;
            double best = 15;

            for (LivingEntity en : Airborne) {
                if (!en.equals(p) && en.isValid() && en.getWorld().equals(p.getWorld())) {
                    double d = en.getLocation().distance(p.getLocation());
                    if (d <= best) {
                        best = d;
                        target = en;
                    }
                }
            }

            if (target == null) {
                p.sendActionBar(Component.text("No airborne targets in range"));
                return;
            }

            LastBreath(p,target);
        }
    }

    @EventHandler
    public void PlayerSneak(PlayerToggleSneakEvent e){
        Player p = e.getPlayer();
        if(!p.isSneaking()){
            RayTraceResult looking = p.getWorld().rayTrace(p.getEyeLocation(),
                    p.getEyeLocation().getDirection(),2,FluidCollisionMode.NEVER,
                    true,1,entity -> entity != p);

            if(looking != null && looking.getHitEntity()!=null && looking.getHitEntity() instanceof LivingEntity en){
                p.setCollidable(false);
                if(!Ecooldown.contains(en)){
                    en.damage(1,p);
                    decayRing(en,1,6*20,20,0.1);
                    States.put(p,YasState.DASHING);
                    new BukkitRunnable() {
                        int x= 0;
                        @Override
                        public void run() {
                            Location loc = p.getLocation();
                            Vector forward = p.getLocation().getDirection();
                            forward.setY(0);
                            forward.normalize().multiply(0.4);
                            Location forwardloc = loc.clone().add(forward);
                            forwardloc.setY(loc.getY());
                            p.teleport(forwardloc);
                            if(x>8 || !p.isValid()){
                                cancel();
                                States.put(p,YasState.IDLE);
                                p.setCollidable(true);
                            }
                            x++;
                        }
                    }.runTaskTimer(plugin,0,1);
                }
                else{
                    p.sendActionBar(Component.text("Target not ready!"));
                }
            }






        }
    }

    @EventHandler
    public void TheSlimening(EntitySpawnEvent e){
        if(e.getEntity() instanceof Slime){
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void PlayerMove(PlayerMoveEvent e){
        Player p = e.getPlayer();
        if(e.getFrom().getBlockX()!=e.getTo().getBlockX() || e.getFrom().getBlockZ()!=e.getTo().getBlockZ()){
            if(Flow.containsKey(p)){
                if(!(Flow.get(p)>=100)){

                    if(Flow.get(p)%2==0){
                        if(p.getInventory().contains(Material.FEATHER)){
                            p.getInventory().addItem(new ItemStack(Material.FEATHER));
                        }
                        else{
                            p.getInventory().setItem(8,new ItemStack(Material.FEATHER));
                        }
                    }
                    Flow.put(p, Flow.get(p) + 1);
                }

            }else{
                p.getInventory().clear(8);
                Flow.put(p,1);
                p.getInventory().setItem(8,new ItemStack(Material.FEATHER,1));
            }
        }
    }

    @EventHandler
    public void DamageDealt(EntityDamageEvent e){
        if(e.getEntity() instanceof Player p){
            if(Flow.containsKey(p) && Flow.get(p)>=100){
                Flow.put(p, 0);
                e.setCancelled(true);
                if(e.getDamage()>4){
                    p.damage(e.getDamage()-4,e.getDamageSource());
                }
                else{
                    p.setAbsorptionAmount(4-e.getDamage());
                }
                p.getInventory().clear(8);

                new BukkitRunnable(){
                    @Override
                    public void run() {
                        p.setAbsorptionAmount(0);
                    }
                }.runTaskLater(plugin,40);
            }
        }
    }
}
