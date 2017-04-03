package TTree;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

import HelperObjects.Dataset;
import HelperObjects.ItemSet;
import HelperObjects.Rule;
import HelperObjects.RuleGenerator;

/*
 * Algorithm variables
 * 
 * R - input dataset
 * N - number of columns/attributes
 * D - number of records
 * ref - reference to current node
 * 
 */

/**
 * Created by Jonathan McDevitt on 2017-03-24.
 */
public class TTree implements RuleGenerator {

    private Node[] start;
    private Dataset dataset;
    private boolean isNewLevel;
    private int minSup;

    private class Node {

        private int sup = 0;
        private Node[] chdRef;

        public boolean hasChildren() {
            for (Node n : this.chdRef) {
                if (n != null) {
                    return true;
                }
            }
            return false;
        }
    }

    public TTree(Dataset dataset, int minSupport) {
        this.minSup = minSupport;
        this.dataset = dataset;

        createTtree();
    }

    private void createTtree() {
        createTtreeTopLevel();
        prune(start, 1);
        genLevelN(start, 1, 1, new ItemSet());

        int K = 2;

        while (isNewLevel) {
            addSupport(K);
            prune(start, K);
            isNewLevel = false;
            genLevelN(start, 1, K, new ItemSet());
            K++;
        }
    }

    private void createTtreeTopLevel() {
        List<List<Integer>> R = dataset.getTable();
        SortedSet<Integer> I = dataset.getValueRangeSet();

        start = new Node[I.size() + 1];
        I.forEach(si -> start[si] = new Node());

        R.forEach(ri -> ri.forEach(sj -> start[sj].sup++));
    }

    private void prune(Node[] ref, int K) {
        if (K == 1) {
            for (int t = 1; t < ref.length; t++) {
                if (ref[t] != null && ref[t].sup < minSup) {
                    ref[t] = null;
                }
            }
        } else {
            for (int t = 1; t < ref.length; t++) {
                if (ref[t] != null && ref[t].chdRef != null) {
                    prune(ref[t].chdRef, K - 1);
                }
            }
        }
    }

    private void addSupport(int K) {
        List<List<Integer>> R = dataset.getTable();
        R.forEach(ri -> addSup(start, K, ri.size(), ri));
    }

    private void addSup(Node[] ref, int K, int end, List<Integer> r) {
        if (K == 1) {
            for (int i = 0; i < end; i++) {
                int si = r.get(i);
                if (si < ref.length && ref[si] != null) {
                    ref[si].sup++;
                }
            }
        } else {
            for (int i = 0; i < end; i++) {
                int si = r.get(i);
                if (si < ref.length && ref[si] != null && ref[si].chdRef != null) {
                    addSup(ref[si].chdRef, K - 1, i, r);
                }
            }
        }
    }

    private void genLevelN(Node[] ref, int K, int newK, ItemSet I) {
        if (K == newK) {
            for (int i = 2; i < ref.length; i++) {
                if (ref[i] != null) {
                    genLevel(ref, i, append(i, I));
                }
            }
        } else {
            for (int i = 2; i < ref.length; i++) {
                if (ref[i] != null && ref[i].chdRef != null) {
                    genLevelN(ref[i].chdRef, K + 1, newK, append(i, I));
                }
            }
        }
    }
    
    private void genLevel(Node[] ref, int end, ItemSet I) {        
        for (int i = 1; i < end; i++) {
            if (ref[i] != null) {
                ItemSet newI = append(i, I);
                if (testCombinations(newI)) {
                    if (ref[end].chdRef == null) {
                        ref[end].chdRef = new Node[end];
                    }
                    ref[end].chdRef[i] = new Node();
                    isNewLevel = true;
                } else {
                    ref[end].chdRef[i] = null;
                }
            }
        }
    }

    private boolean testCombinations(ItemSet I) {
        if (I.size() < 3) {
            return true;
        }

        ItemSet I1 = doubleton(I.get(1), I.get(0));
        ItemSet I2 = delN(I, 2);

        return combinations(new ItemSet(), 0, 2, I1, I2);
    }

    private boolean combinations(ItemSet I, int start, int end, ItemSet I1, ItemSet I2) {
        if (end > I2.size()) {
            ItemSet testSet = append(I, I1);
            return findInTtree(testSet);
        } else {
            for (int i = start; i < end; i++) {
                ItemSet tempSet = append(I2.get(i), I);
                if (!combinations(tempSet, i + 1, end + 1, I1, I2)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean findInTtree(ItemSet I) {
        return findInTtree(I, I.size() - 1, start);
    }

    private boolean findInTtree(ItemSet I, int k, Node[] ref) {
        if (k > 0) {
            return true;
        } else if (ref == null) {
            return false;
        } else {
            int next = I.get(k);
            if (ref[next] != null) {
                return findInTtree(I, k - 1, ref[next].chdRef);
            } else {
                return false;
            }
        }
    }

    private static ItemSet append(int i, ItemSet I) {
        ItemSet ret = new ItemSet(I);
        ret.append(i);
        return ret;
    }

    private static ItemSet append(ItemSet I, ItemSet J) {
        ItemSet ret = new ItemSet(I);
        ret.append(J);
        return ret;
    }

    private static ItemSet doubleton(int i, int j) {
        ItemSet ret = new ItemSet();
        ret.append(i);
        ret.append(j);
        return ret;
    }

    private static ItemSet delN(ItemSet I, int N) {
        ItemSet ret = new ItemSet(I);
        ret.remove(N);
        return ret;
    }

    @Override
    public List<Rule> generateRules(double confidence) {
        return generateRules(confidence, start, null, 0);
    }

    List<Rule> generateRules(double confidence, Node[] layer, List<Integer> path, int level) {
        List<Rule> rules = new ArrayList<>();
        for(int i = 0; i < layer.length; i++) {
            /** If we are at the beginning, initialize the path */
            if(path == null) {
                path = new ArrayList<>();
            }
            Node n = layer[i];
            /** If our value is not null, then we can begin to evaluate as a tree   */
            if(n != null) {

                /** If we are not null, then we can add ourselves to the path and construct rules involving ourselves
                 *  */
                path.add(i);
                rules.addAll(generatePartitions(confidence, path));

                /** If we have children, then we need to generate rules including these children    */
                if(n.hasChildren()) {
                    rules.addAll(generateRules(confidence, n.chdRef, path, level + 1));
                }
                /** Clear the path before moving to the next node   */
                for(int j = path.size()-1; j >= level; j--) {
                    path.remove(j);
                }
            }
        }
        return rules;
    }

    private List<Rule> generatePartitions(double confidence, List<Integer> path) {
        return new ArrayList<>();
    }
    
}
