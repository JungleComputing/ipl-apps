package ibis.ipl.apps.sor;

import java.io.IOException;

public interface ReducerInterface {
    
    public double reduce(double value) throws IOException;
    
    public void end() throws IOException;
}
