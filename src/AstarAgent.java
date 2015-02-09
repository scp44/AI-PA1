//package edu.cwru.sepia.agent;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.ResourceNode;
import edu.cwru.sepia.environment.model.state.State;
import edu.cwru.sepia.environment.model.state.Unit;
import edu.cwru.sepia.util.Direction;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

public class AstarAgent extends Agent {

    class MapLocation
    {
        public int x, y;
        MapLocation cameFrom;
        public float f_score, g_score;

        public MapLocation(int x, int y, MapLocation cameFrom, float g_score)
        {
        	this.cameFrom = cameFrom;
        	this.g_score = g_score;
            this.x = x;
            this.y = y;
        }
        @Override
        public boolean equals(Object location) 
        {
        	return (this.x == ((MapLocation)location).x && this.y == ((MapLocation)location).y);
        }
    }
    
    class OpenListCompare implements Comparator<MapLocation>
    {
    	public int compare(MapLocation loc1, MapLocation loc2) {
    		if (loc1.f_score < loc2.f_score)
    			return -1;
    		else if (loc1.f_score == loc2.f_score)
    			return 0;
    		else
    			return 1;
    	}
    }
    

    Stack<MapLocation> path;
    int footmanID, townhallID, enemyFootmanID;
    MapLocation nextLoc;

    private long totalPlanTime = 0; // nsecs
    private long totalExecutionTime = 0; //nsecs

    public AstarAgent(int playernum)
    {
        super(playernum);

        System.out.println("Constructed AstarAgent");
    }

    @Override
    public Map<Integer, Action> initialStep(State.StateView newstate, History.HistoryView statehistory) {
        // get the footman location
        List<Integer> unitIDs = newstate.getUnitIds(playernum);

        if(unitIDs.size() == 0)
        {
            System.err.println("No units found!");
            return null;
        }

        footmanID = unitIDs.get(0);

        // double check that this is a footman
        if(!newstate.getUnit(footmanID).getTemplateView().getName().equals("Footman"))
        {
            System.err.println("Footman unit not found");
            return null;
        }

        // find the enemy playernum
        Integer[] playerNums = newstate.getPlayerNumbers();
        int enemyPlayerNum = -1;
        for(Integer playerNum : playerNums)
        {
            if(playerNum != playernum) {
                enemyPlayerNum = playerNum;
                break;
            }
        }

        if(enemyPlayerNum == -1)
        {
            System.err.println("Failed to get enemy playernumber");
            return null;
        }

        // find the townhall ID
        List<Integer> enemyUnitIDs = newstate.getUnitIds(enemyPlayerNum);

        if(enemyUnitIDs.size() == 0)
        {
            System.err.println("Failed to find enemy units");
            return null;
        }

        townhallID = -1;
        enemyFootmanID = -1;
        for(Integer unitID : enemyUnitIDs)
        {
            Unit.UnitView tempUnit = newstate.getUnit(unitID);
            String unitType = tempUnit.getTemplateView().getName().toLowerCase();
            if(unitType.equals("townhall"))
            {
                townhallID = unitID;
            }
            else if(unitType.equals("footman"))
            {
                enemyFootmanID = unitID;
            }
            else
            {
                System.err.println("Unknown unit type");
            }
        }

        if(townhallID == -1) {
            System.err.println("Error: Couldn't find townhall");
            return null;
        }

        long startTime = System.nanoTime();
        path = findPath(newstate);
        totalPlanTime += System.nanoTime() - startTime;

        return middleStep(newstate, statehistory);
    }

    @Override
    public Map<Integer, Action> middleStep(State.StateView newstate, History.HistoryView statehistory) {
        long startTime = System.nanoTime();
        long planTime = 0;

        Map<Integer, Action> actions = new HashMap<Integer, Action>();

        if(shouldReplanPath(newstate, statehistory, path)) {
            long planStartTime = System.nanoTime();
            path = findPath(newstate);
            planTime = System.nanoTime() - planStartTime;
            totalPlanTime += planTime;
        }

        Unit.UnitView footmanUnit = newstate.getUnit(footmanID);

        int footmanX = footmanUnit.getXPosition();
        int footmanY = footmanUnit.getYPosition();

        if(!path.empty() && (nextLoc == null || (footmanX == nextLoc.x && footmanY == nextLoc.y))) {

            // stat moving to the next step in the path
            nextLoc = path.pop();

            System.out.println("Moving to (" + nextLoc.x + ", " + nextLoc.y + ")");
        }

        if(nextLoc != null && (footmanX != nextLoc.x || footmanY != nextLoc.y))
        {
            int xDiff = nextLoc.x - footmanX;
            int yDiff = nextLoc.y - footmanY;

            // figure out the direction the footman needs to move in
            Direction nextDirection = getNextDirection(xDiff, yDiff);

            actions.put(footmanID, Action.createPrimitiveMove(footmanID, nextDirection));
        } else {
            Unit.UnitView townhallUnit = newstate.getUnit(townhallID);

            // if townhall was destroyed on the last turn
            if(townhallUnit == null) {
                terminalStep(newstate, statehistory);
                return actions;
            }

            if(Math.abs(footmanX - townhallUnit.getXPosition()) > 1 ||
                    Math.abs(footmanY - townhallUnit.getYPosition()) > 1)
            {
                System.err.println("Invalid plan. Cannot attack townhall");
                totalExecutionTime += System.nanoTime() - startTime - planTime;
                return actions;
            }
            else {
                System.out.println("Attacking TownHall");
                // if no more movements in the planned path then attack
                actions.put(footmanID, Action.createPrimitiveAttack(footmanID, townhallID));
            }
        }

        totalExecutionTime += System.nanoTime() - startTime - planTime;
        return actions;
    }

    @Override
    public void terminalStep(State.StateView newstate, History.HistoryView statehistory) {
        System.out.println("Total turns: " + newstate.getTurnNumber());
        System.out.println("Total planning time: " + totalPlanTime/1e9);
        System.out.println("Total execution time: " + totalExecutionTime/1e9);
        System.out.println("Total time: " + (totalExecutionTime + totalPlanTime)/1e9);
    }

    @Override
    public void savePlayerData(OutputStream os) {

    }

    @Override
    public void loadPlayerData(InputStream is) {

    }

    /**
     * You will implement this method.
     *
     * This method should return true when the path needs to be replanned
     * and false otherwise. This will be necessary on the dynamic map where the
     * footman will move to block your unit.
     *
     * @param state
     * @param history
     * @param currentPath
     * @return
     */
    private boolean shouldReplanPath(State.StateView state, History.HistoryView history, Stack<MapLocation> currentPath)
    {
        return false;
    }

    /**
     * This method is implemented for you. You should look at it to see examples of
     * how to find units and resources in Sepia.
     *
     * @param state
     * @return
     */
    private Stack<MapLocation> findPath(State.StateView state)
    {
        Unit.UnitView townhallUnit = state.getUnit(townhallID);
        Unit.UnitView footmanUnit = state.getUnit(footmanID);

        MapLocation startLoc = new MapLocation(footmanUnit.getXPosition(), footmanUnit.getYPosition(), null, 0);

        MapLocation goalLoc = new MapLocation(townhallUnit.getXPosition(), townhallUnit.getYPosition(), null, 0);

        MapLocation footmanLoc = null;
        if(enemyFootmanID != -1) {
            Unit.UnitView enemyFootmanUnit = state.getUnit(enemyFootmanID);
            footmanLoc = new MapLocation(enemyFootmanUnit.getXPosition(), enemyFootmanUnit.getYPosition(), null, 0);
        }

        // get resource locations
        List<Integer> resourceIDs = state.getAllResourceIds();
        Set<MapLocation> resourceLocations = new HashSet<MapLocation>();
        for(Integer resourceID : resourceIDs)
        {
            ResourceNode.ResourceView resource = state.getResourceNode(resourceID);

            resourceLocations.add(new MapLocation(resource.getXPosition(), resource.getYPosition(), null, 0));
        }

        return AstarSearch(startLoc, goalLoc, state.getXExtent(), state.getYExtent(), footmanLoc, resourceLocations);
    }
    /**
     * This is the method you will implement for the assignment. Your implementation
     * will use the A* algorithm to compute the optimum path from the start position to
     * a position adjacent to the goal position.
     *
     * You will return a Stack of positions with the top of the stack being the first space to move to
     * and the bottom of the stack being the last space to move to. If there is no path to the townhall
     * then return null from the method and the agent will print a message and do nothing.
     * The code to execute the plan is provided for you in the middleStep method.
     *
     * As an example consider the following simple map
     *
     * F - - - -
     * x x x - x
     * H - - - -
     *
     * F is the footman
     * H is the townhall
     * x's are occupied spaces
     *
     * xExtent would be 5 for this map with valid X coordinates in the range of [0, 4]
     * x=0 is the left most column and x=4 is the right most column
     *
     * yExtent would be 3 for this map with valid Y coordinates in the range of [0, 2]
     * y=0 is the top most row and y=2 is the bottom most row
     *
     * resourceLocations would be {(0,1), (1,1), (2,1), (4,1)}
     *
     * The path would be
     *
     * (1,0)
     * (2,0)
     * (3,1)
     * (2,2)
     * (1,2)
     *
     * Notice how the initial footman position and the townhall position are not included in the path stack
     *
     * @param start Starting position of the footman
     * @param goal MapLocation of the townhall
     * @param xExtent Width of the map
     * @param yExtent Height of the map
     * @param resourceLocations Set of positions occupied by resources
     * @return Stack of positions with top of stack being first move in plan
     */
    private Stack<MapLocation> AstarSearch(MapLocation start, MapLocation goal, int xExtent, int yExtent, MapLocation enemyFootmanLoc, Set<MapLocation> resourceLocations)
    {
    	{
    		PriorityQueue<MapLocation> openList = new PriorityQueue<MapLocation>(0, new OpenListCompare());
        	Set<MapLocation> closedList = new HashSet<MapLocation>();
        	
        	start.g_score = 0;
        	start.f_score = start.g_score + h(start, goal);
        	openList.add(start);
        	
        	MapLocation current = null;
        	MapLocation neighbor = null;
        	float temp_g = 0;
        	
        	while(!openList.isEmpty())
        	{
        		//remove invalid nodes from the list until first valid one is found
        		while(openList.peek().f_score == -1)
        		{
        			openList.remove();
        		}
        		
        		current = openList.poll();
	        	closedList.add(current);
	        	
        		if (current.equals(goal))
        		{
        			//reconstruct path & return it
        			System.out.println("Goal found.\n");
        			return reconstructPath(current, start);
        		}
        		        		
        		MapLocation[] neighborList = getNeighbor(current, xExtent, yExtent, resourceLocations);

        		for(int x = 0; x < neighborList.length && neighborList[x] != null; x++)
        		{
        			neighbor = neighborList[x];
        			
        			if (closedList.contains(neighbor))
        			{
        				continue;
        			}
        			
        			temp_g = current.g_score + 1;
        			
        			if(!(openList.contains(neighbor)) || temp_g < neighbor.g_score )
        			{
        				neighbor.cameFrom = current;
        				neighbor.g_score = temp_g;
        				neighbor.f_score = neighbor.g_score + h(neighbor, goal);
        				
        				if(!(openList.contains(neighbor)))
        				{
        					openList.add(neighbor);
        				}
        				else
        				{
        					//somehow invalidate/remove the old one here
        					openList.add(neighbor);
        				}
        			}
        		}
        		
//        		for (Iterator<MapLocation> it = closedList.iterator(); it.hasNext(); ) {
//        			node = it.next();
//        	        System.out.println("Closed list contents: x= " + node.x + " y = " + node.y + " size = " + 
//        	        		closedList.size());
//        	    }
        	}
        	
        }
    	
    	//no path. Return empty path
        return new Stack<MapLocation>();
    }

    private Stack<MapLocation> reconstructPath(MapLocation current, MapLocation start)
    {
    	Stack<MapLocation> path = new Stack<MapLocation>();
    	path.add(current);
    	
    	while(!(current.cameFrom.equals(start)))
    	{
    		current = current.cameFrom;
    		path.add(current);
    	}
    	
    	return path;
    }
    
    private int h(MapLocation current, MapLocation goal)
    {
    	int distance = 0;
    	distance = Math.max(Math.abs(goal.x-current.x), Math.abs(goal.y-current.y));
    	return distance;
    }
    
    private MapLocation[] getNeighbor(MapLocation current, int xExtent, int yExtent, Set<MapLocation> resourceLocations) 
    {
    	MapLocation[] neighbors = new MapLocation[8];
    	int index = 0;
    	if(current.x - 1 >= 0 && current.y - 1 >= 0 && resourceLocations.contains( 
    			new MapLocation(current.x - 1, current.y - 1, null, 0))) {
			neighbors[index++] = new MapLocation(current.x - 1, current.y - 1, current, 0);
		}
		if(current.x - 1 >= 0 && current.y >= 0 && resourceLocations.contains( 
				new MapLocation(current.x - 1, current.y, null, 0))) {
			neighbors[index++] = new MapLocation(current.x - 1, current.y, current, 0);
		}
		if(current.x - 1 >= 0 && current.y + 1 < yExtent && resourceLocations.contains( 
				new MapLocation(current.x - 1, current.y + 1, null, 0))) {
			neighbors[index++] = new MapLocation(current.x - 1, current.y + 1, current, 0);
		}
		if(current.x >= 0 && current.y - 1 >= 0 && resourceLocations.contains( 
				new MapLocation(current.x, current.y - 1, null, 0))) {
			neighbors[index++] = new MapLocation(current.x, current.y - 1, current, 0);
		}
		if(current.x >= 0 && current.y + 1 < yExtent && resourceLocations.contains( 
				new MapLocation(current.x, current.y + 1, null, 0))) {
			neighbors[index++] = new MapLocation(current.x, current.y + 1, current, 0);
		}
		if(current.x + 1 < xExtent && current.y - 1 >= 0 && resourceLocations.contains( 
				new MapLocation(current.x + 1, current.y - 1, null, 0))) {
			neighbors[index++] = new MapLocation(current.x + 1, current.y - 1, current, 0);
		}
		if(current.x + 1 < xExtent && current.y >= 0 && resourceLocations.contains( 
				new MapLocation(current.x + 1, current.y, null, 0))) {
			neighbors[index++] = new MapLocation(current.x + 1, current.y, current, 0);
		}
		if(current.x + 1 < xExtent && current.y + 1 <= yExtent && resourceLocations.contains( 
				new MapLocation(current.x + 1, current.y + 1, null, 0))) {
			neighbors[index++] = new MapLocation(current.x + 1, current.y + 1, current, 0);
		}
		return neighbors;
    }
    
    
    //Obselete method 
    /*private boolean setContains(Set<MapLocation> set, MapLocation loc) {
    	
    	if (set.isEmpty()) 
    		return false;
    	Iterator<MapLocation> it = set.iterator();
    	while (it.hasNext()) {
    		MapLocation location = it.next();
			if(location.x == loc.x && location.y == loc.y) {
				return true;
			}
	    }
    	return false;
    }*/
    /**
     * Primitive actions take a direction (e.g. NORTH, NORTHEAST, etc)
     * This converts the difference between the current position and the
     * desired position to a direction.
     *
     * @param xDiff Integer equal to 1, 0 or -1
     * @param yDiff Integer equal to 1, 0 or -1
     * @return A Direction instance (e.g. SOUTHWEST) or null in the case of error
     */
    private Direction getNextDirection(int xDiff, int yDiff) {

        // figure out the direction the footman needs to move in
        if(xDiff == 1 && yDiff == 1)
        {
            return Direction.SOUTHEAST;
        }
        else if(xDiff == 1 && yDiff == 0)
        {
            return Direction.EAST;
        }
        else if(xDiff == 1 && yDiff == -1)
        {
            return Direction.NORTHEAST;
        }
        else if(xDiff == 0 && yDiff == 1)
        {
            return Direction.SOUTH;
        }
        else if(xDiff == 0 && yDiff == -1)
        {
            return Direction.NORTH;
        }
        else if(xDiff == -1 && yDiff == 1)
        {
            return Direction.SOUTHWEST;
        }
        else if(xDiff == -1 && yDiff == 0)
        {
            return Direction.WEST;
        }
        else if(xDiff == -1 && yDiff == -1)
        {
            return Direction.NORTHWEST;
        }

        System.err.println("Invalid path. Could not determine direction");
        return null;
    }
}
