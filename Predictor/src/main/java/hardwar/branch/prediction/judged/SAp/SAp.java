package hardwar.branch.prediction.judged.SAp;

import hardwar.branch.prediction.shared.*;
import hardwar.branch.prediction.shared.devices.*;

import java.util.Arrays;

public class SAp implements BranchPredictor {

    private final int branchInstructionSize;
    private final int KSize;
    private final ShiftRegister SC;
    private final RegisterBank PSBHR; // per set branch history register
    private final Cache<Bit[], Bit[]> PAPHT; // per address predication history table

    public SAp() {
        this(4, 2, 8, 4);
    }

    public SAp(int BHRSize, int SCSize, int branchInstructionSize, int KSize) {
        // TODO: complete the constructor
        this.branchInstructionSize = branchInstructionSize;
        this.KSize = KSize;

        // Initialize the PABHR with the given bhr and branch instruction size
        PSBHR = new RegisterBank(KSize, BHRSize);

        // Initializing the PAPHT with K bit as PHT selector and 2^BHRSize row as each
        // PHT entries
        // number and SCSize as block size
        PAPHT = new PerAddressPredictionHistoryTable(
                branchInstructionSize, 1 << BHRSize, SCSize);

        // Initialize the saturating counter
        this.SC = new SIPORegister("SC", SCSize, null);
    }

    @Override
    public BranchResult predict(BranchInstruction branchInstruction) {
        // get BHR value
        ShiftRegister BHR = PSBHR.read(hash(branchInstruction.getInstructionAddress()));
        // concatenate the instruction address
        // hashing the address
        Bit[] hash = getCacheEntry(branchInstruction.getInstructionAddress(), BHR.read());

        // initialize the PHT if empty
        PAPHT.putIfAbsent(hash, getDefaultBlock());
        // read from the cache
        Bit[] block = PAPHT.get(hash);
        // load into the SC register
        SC.load(block);
        return BranchResult.of(block[0].getValue());
    }

    @Override
    public void update(BranchInstruction instruction, BranchResult actual) {
        // counting from the SC register
        Bit[] counted = CombinationalLogic.count(this.SC.read(),
                BranchResult.isTaken(actual), CountMode.SATURATING);
        // updating our cache
        // get BHR value
        ShiftRegister BHR = PSBHR.read(hash(instruction.getInstructionAddress()));
        Bit[] hash = getCacheEntry(instruction.getInstructionAddress(), BHR.read());

        PAPHT.put(hash, counted);
        // updating the BHR
        BHR.insert(Bit.of(BranchResult.isTaken(actual)));
        PSBHR.write(hash, BHR.read());
    }

    private Bit[] getRBAddressLine(Bit[] branchAddress) {
        // hash the branch address
        return hash(branchAddress);
    }

    private Bit[] getCacheEntry(Bit[] branchAddress, Bit[] BHRValue) {
        // Concatenate the branch address bits with the BHR bits
        Bit[] cacheEntry = new Bit[branchAddress.length + BHRValue.length];
        System.arraycopy(branchAddress, 0, cacheEntry, 0, branchInstructionSize);
        System.arraycopy(BHRValue, 0, cacheEntry, branchAddress.length, BHRValue.length);
        return cacheEntry;
    }

    /*
     * 
     * hash N
     * bits to
     * a K
     * bit value**
     * 
     * @param bits program counter*@return
     * hash value
     * of fist
     * M bits of`bits`
     * in K bits
     */

    private Bit[] hash(Bit[] bits) {
        Bit[] hash = new Bit[KSize];

        // XOR the first M bits of the PC to produce the hash
        for (int i = 0; i < branchInstructionSize; i++) {
            int j = i % KSize;
            if (hash[j] == null) {
                hash[j] = bits[i];
            } else {
                Bit xorProduce = hash[j].getValue() ^ bits[i].getValue() ? Bit.ONE : Bit.ZERO;
                hash[j] = xorProduce;

            }
        }
        return hash;
    }

    /*
     * @return
     * 
     * a zero
     * series of
     * bits as default
     * value of
     * cache block
     */

    private Bit[] getDefaultBlock() {
        Bit[] defaultBlock = new Bit[SC.getLength()];
        Arrays.fill(defaultBlock, Bit.ZERO);
        return defaultBlock;
    }

    @Override
    public String monitor() {
        return null;
    }
}