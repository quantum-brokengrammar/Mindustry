package mindustry.ai.types;

import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.ai.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.world.*;

public class CommandAI extends AIController{
    private static final float localInterval = 40f;
    private static final Vec2 vecOut = new Vec2(), flockVec = new Vec2(), separation = new Vec2(), cohesion = new Vec2(), massCenter = new Vec2();

    public @Nullable Vec2 targetPos;
    public @Nullable Teamc attackTarget;

    private Vec2 lastTargetPos;
    private int pathId = -1;
    private Seq<Unit> local = new Seq<>(false);
    private boolean flocked;

    @Override
    public void updateUnit(){
        updateVisuals();
        updateTargeting();

        if(attackTarget != null && invalid(attackTarget)){
            attackTarget = null;
            targetPos = null;
        }

        if(targetPos != null){
            if(timer.get(timerTarget3, localInterval) || !flocked){
                if(!flocked){
                    //make sure updates are staggered randomly
                    timer.reset(timerTarget3, Mathf.random(localInterval));
                }

                local.clear();
                //TODO experiment with 2/3/4
                float size = unit.hitSize * 3f;
                unit.team.data().tree().intersect(unit.x - size / 2f, unit.y - size/2f, size, size, local);
                local.remove(unit);
                flocked = true;
            }
        }else{
            flocked = false;
        }

        if(attackTarget != null){
            if(targetPos == null){
                targetPos = new Vec2();
                lastTargetPos = targetPos;
            }
            targetPos.set(attackTarget);

            if(unit.isGrounded() && attackTarget instanceof Building build && build.tile.solid() && unit.pathType() != Pathfinder.costLegs){
                Tile best = build.findClosestEdge(unit, Tile::solid);
                if(best != null){
                    targetPos.set(best);
                }
            }
        }

        if(targetPos != null){
            boolean move = true;
            vecOut.set(targetPos);

            if(unit.isGrounded()){
                move = Vars.controlPath.getPathPosition(unit, pathId, targetPos, vecOut);
            }

            float engageRange = unit.type.range - 10f;

            if(move){
                moveTo(vecOut,
                    attackTarget != null && unit.within(attackTarget, engageRange) ? engageRange :
                    unit.isGrounded() ? 0f :
                    attackTarget != null ? engageRange :
                    0f, 100f, false, null);

                //calculateFlock().limit(unit.speed() * flockMult)
            }

            if(unit.isFlying()){
                unit.lookAt(targetPos);
            }else{
                faceTarget();
            }

            if(attackTarget == null){
                if(unit.within(targetPos, Math.max(5f, unit.hitSize / 2f))){
                    targetPos = null;
                }else if(local.size > 1){
                    int count = 0;
                    for(var near : local){
                        //has arrived
                        if(near.isCommandable() && !near.command().hasCommand() && targetPos.equals(near.command().lastTargetPos)){
                            count ++;
                        }
                    }

                    //others have arrived at destination, so this one will too
                    if(count >= Math.max(2, local.size / 3)){
                        targetPos = null;
                    }
                }
            }

        }else if(target != null){
            faceTarget();
        }
    }

    public static float cohesionScl = 0.3f;
    public static float cohesionRad = 3f, separationRad = 1.1f, separationScl = 1f, flockMult = 0.5f;

    //TODO ひどい
    Vec2 calculateFlock(){
        if(local.isEmpty()) return flockVec.setZero();

        flockVec.setZero();
        separation.setZero();
        cohesion.setZero();
        massCenter.set(unit);

        float rad = unit.hitSize;
        float sepDst = rad * separationRad, cohDst = rad * cohesionRad;

        //"cohesed" isn't even a word smh
        int separated = 0, cohesed = 1;

        for(var other : local){
            float dst = other.dst(unit);
            if(dst < sepDst){
                separation.add(Tmp.v1.set(unit).sub(other).scl(1f / sepDst));
                separated ++;
            }

            if(dst < cohDst){
                massCenter.add(other);
                cohesed ++;
            }
        }

        if(separated > 0){
            separation.scl(1f / separated);
            flockVec.add(separation.scl(separationScl));
        }

        if(cohesed > 1){
            massCenter.scl(1f / cohesed);
            flockVec.add(Tmp.v1.set(massCenter).sub(unit).limit(cohesionScl * unit.type.speed));
            //seek mass center?
        }

        return flockVec;
    }

    @Override
    public boolean keepState(){
        return true;
    }

    @Override
    public Teamc findTarget(float x, float y, float range, boolean air, boolean ground){
        return attackTarget == null ? super.findTarget(x, y, range, air, ground) : attackTarget;
    }

    @Override
    public boolean retarget(){
        //retarget instantly when there is an explicit target, there is no performance cost
        return attackTarget != null || timer.get(timerTarget, 20);
    }

    public boolean hasCommand(){
        return targetPos != null;
    }

    public void commandPosition(Vec2 pos){
        targetPos = pos;
        lastTargetPos = pos;
        attackTarget = null;
        pathId = Vars.controlPath.nextTargetId();
    }

    public void commandTarget(Teamc moveTo){
        //TODO
        attackTarget = moveTo;
        pathId = Vars.controlPath.nextTargetId();
    }
}
