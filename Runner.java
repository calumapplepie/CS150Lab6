import java.io.FileWriter;
import java.util.Random;

/**
 * This class is responsible for actually running the tests.
 * Pay no attention to the fact that it apparently uses multithreading;
 * that's not something that's taught in CS150
 *
 * @author Calum McConnell
 * @version 42
 */
public class Runner
{
    public static void main(String[] args){
        FileWriter fileOutput;
        try{
            // open the file
            fileOutput = new FileWriter("output");
            // put some column headers in
            fileOutput.write("randomSeed,nValue,searchesNotFound,bubSort,fastBubSort,linSearchMin,linSearchAvg,linSearchMax,binSearchMin,binSearchAvg,binSearchMax,");
            fileOutput.write("\n");
        }
        catch(Exception e){
            System.out.println("things broke");
            System.err.println(e);
            return;
        }
        
        // get a list of the N-values we will be using
        int[] nVals = {10,100,1_000,10_000,100_000};//,1_000_000};
        
        // now, for the multithreading.  Lets make an array to hold the threads
        // I created.  this way, I can get a 'progress bar'
        Thread[] threads = new Thread[nVals.length*20];
        // this represents the index we are inserting into within the array
        int dex = 0;
        
        // build a thread for each value of N
        for(int i: nVals){
            // our seeds are just 00000,00002, etc... it doesn't matter, the
            // random class is good enough.
            for(int j = 0; j < 20; j++){
                // capturing lambdas can only work with local variables 
                // that are never modified, so we need to 'clone' j.
                int k = j;
                // now, we summon ThreadThulu
                Thread t = new Thread(() ->runAndEvaluateSorts(k,i,fileOutput));
                // place ThreadThulu in the array
                threads[dex++] = t;
                // now let 'er rip
                t.start();
            }
        }
        
        // now, we wait.  but that's boring, so lets get a status printing
        
        // loop until all threads are done
        boolean allDone = false;
        while(!allDone){
            // count how many are done
            int numDone = 0;
            for(Thread t : threads){
                if(!t.isAlive()){
                    numDone++;
                }
            }
            System.out.printf("Progress: %3d/%3d%n",numDone,threads.length);
            if(numDone == threads.length){
                // yes, we don't bother changing allDone.  i
                // can never decide how to structure a loop like this
                break;
            }
            // now sleep for 5 secconds, give the threads time to actually run
            try{
                Thread.sleep(5000);
            }
            catch(Exception e){
                // this can't happen, since we never use thread interrupts.
                System.out.println("Danger: angry pixies");
                return;
            }
        }
        System.out.println("goodbye!");
        // Make sure the file is actually written, because Java is lazy
        try{
            fileOutput.close();
        }
        catch(Exception e){
            System.out.println("couldn't write out data");
            System.err.println(e);
        }
    }
    
    /**
     * This method will be used as a runable, and will be running multiple
     * times simultaneuously.  In the interests of thread-saftey, it must
     * therefore behave in a specific way, and I need to be careful about
     * what objects it can access (since unsynchronized access means data
     * races and problems).
     * 
     * Fortunately, its very easy in this case (which is why I am using
     * threads, rather than just doing it sequentially).  The thread needs
     * to access almost nothing outside of itself, and what it does need
     * is primitive types (which can't data race anyways in java). The
     * FileWriter class is synchronized: a review of the docs shows that
     * it will not modify the file concurrently within a single
     * method call, which would corrupt our data.
     * I pass it in as a parameter just to make it really clear that 
     * I know what firey torches I am juggling here.
     */
    public static void runAndEvaluateSorts(int seed, int N, FileWriter writeMe){
        // we'll be using and re-using this variable, usually with the
        // same seed.
        Random numberForge = new Random(seed);
        
        // now build our data array
        int [] data = new int[N];
        for(int i = 0; i < N; i++){
            data[i] = numberForge.nextInt();
        }
        // now build our sorter objects, being sure to avoid
        // them both just sorting the same array
        BubbleSort bubSorter = new BubbleSort(data.clone());
        FasterBubbleSort fastSorter = new FasterBubbleSort(data);
        
        // run the algorithms
        // the lab mentions putting them in an array, than looping through it:
        // that seems very extra, and it's not in the requirements
        bubSorter.run_algorithm();
        fastSorter.run_algorithm();
        // the variable "data" is now sorted, 
        // because we let it alias in fastSorter
        
        // build the searcher objects, letting the array alias
        // this avoids an O(N) copy, which will take a bit 
        LinearSearch linSearcher = new LinearSearch(data);
        BinarySearch binSearcher = new BinarySearch(data);
        
        // reset our RNG to one more than the seed, getting a whole new
        // sequence of values, so that we don't just search for the values
        // in order
        numberForge = new Random(seed+1);
        
        // declare the min/max/avg variables for the searches
        long linearMin=0,linearMax=0;
        long binaryMin=0,binaryMax=0;
        
        // use doubles for averages: this change was made after
        // kicking off the million run on my desktop, so, if it
        // finishes in time to make it into the report, the data 
        // from it (over 100,000) will not contain decimals.
        double linearAvg=0,binaryAvg=0;
        
        int failedSearches = 0;
        
        // now, lets go!
        for(int i = 0; i < N; i++){
            // get a target (this also resets the counter)
            int target = numberForge.nextInt();
            linSearcher.resetSearchTarget(target);
            binSearcher.resetSearchTarget(target);
            
            // find the target
            linSearcher.run_algorithm();
            binSearcher.run_algorithm();
            
            // see how long it took
            long linComplexity = linSearcher.result();
            long binComplexity = binSearcher.result();
            
            // check if the search failed
            if(binSearcher.getLocatedIndex() == -1){
                failedSearches++;
            }
            
            // if it's the first loop, we need to properly
            // set up our maxs, mins, and avgs, so they aren't stuck
            // at zero.
            if(i == 0){
                linearMin = linComplexity;
                linearMax = linComplexity;
                linearAvg = linComplexity;
                binaryMin = binComplexity;
                binaryMax = binComplexity;
                binaryAvg = binComplexity;
                // a divide-by-zero in the averaging code will result
                // if we don't exit now
                continue;
            }
            
            // update the maxes and mins accordingly
            linearMin = Math.min(linearMin,linComplexity);
            linearMax = Math.max(linearMax,linComplexity);
            binaryMin = Math.min(binaryMin,binComplexity);
            binaryMax = Math.max(binaryMax,binComplexity);
            
            // now, we calculate the average.
            // when calculating an average after adding a value, 
            // the sum of the rest of the list is equal to the average
            // of the rest of the list multiplied by the number of elements
            // so, we do that, add our new element, and then divide
            linearAvg = (linearAvg * i + linComplexity) / (i+1);
            binaryAvg = (binaryAvg * i + binComplexity) / (i+1);
            
            
        }
        // Now, build a string that reflects our data: we need to print with one
        // funciton call, or else
        String csvFileLine = "";
        
        // the format is:
//"randomSeed,nValue,searchesNotFound,bubSort,fastBubSort,linSearchMin,linSearchAvg,linSearchMax,binSearchMin,binSearchAvg,binSearchMax");
        
        // i hate repetition, but java doesn't provide a CSV class by default,
        // and there's no way to add a function to do this without putting it
        // in another class or making csvFileLine static.  making it static
        // would make it impossible to synchronize with threads: making it in
        // another class would violate the spec, which asks for all this stuff
        // to be in one class
        csvFileLine += seed + ",";
        csvFileLine += N + ",";
        csvFileLine += failedSearches + ",";
        
        // add the sort complexities
        csvFileLine += bubSorter.result() + ",";
        csvFileLine += fastSorter.result() + ",";
        
        // Now the linear search
        csvFileLine += linearMin + ",";
        csvFileLine += linearAvg + ",";
        csvFileLine += linearMax + ",";
        
        // and the binary
        csvFileLine += binaryMin + ",";
        csvFileLine += binaryAvg + ",";
        csvFileLine += binaryMax + ",\n";
        
        // now, we write out the line
        try{
            writeMe.write(csvFileLine);
        }
        catch(Exception e){
            System.out.println("a writing error has occured!");
            System.err.println(e);
        }
    }
}
