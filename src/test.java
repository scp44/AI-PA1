import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

//import AstarAgent.MapLocation;




public class test {

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
        public boolean isEqual(MapLocation location) 
        {
        	return (this.x == location.x && this.y == location.y);
        }
    }
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		test myTest = new test();
		myTest.helper();
	}
	
	private void helper()
	{
		Set<MapLocation> openList = new HashSet<MapLocation>();
		MapLocation loc = new MapLocation(1, 1, null, 0);
		MapLocation loc2 = new MapLocation(2, 2, null, 0);
		openList.add(loc);
		System.out.println(setContains(openList, new MapLocation(0, 1, null, 0)));
	}
	
	private boolean setContains(Set<MapLocation> set, MapLocation loc) {
    	
    	for (Iterator<MapLocation> it = set.iterator(); it.hasNext();) {
    		MapLocation location = it.next();
    		System.out.println(location.x + " " +  location.y);
			if(location.x == loc.x && location.y == loc.y) {
				return true;
			}
	    }
    	return false;
    }

}
