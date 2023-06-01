package hardwar.branch.prediction.judged.PAs;

import hardwar.branch.prediction.shared.*;
import hardwar.branch.prediction.shared.devices.*;

import java.util.Arrays;

public class PAs implements BranchPredictor {

    private final int branchInstructionSize;
    private final int KSize;
    private final HashMode hashMode;
    private final ShiftRegister SC; // saturating counter register
    private final RegisterBank PABHR; // per address Branch History Register
    private final Cache<Bit[], Bit[]> PSPHT; // Per Set Predication History Table

    public PAs() {
        this(4, 2, 8, 4, HashMode.XOR);
    }

    public PAs(int BHRSize, int SCSize, int branchInstructionSize, int KSize, HashMode hashMode) {
        this.branchInstructionSize = branchInstructionSize;
        this.KSize = KSize;
        this.hashMode = hashMode;

        // Initialize the PABHR with the given bhr and branch instruction size
        PABHR = new RegisterBank(branchInstructionSize, BHRSize);

        // Initializing the PAPHT with K bit as PHT selector and 2^BHRSize row as each
        // PHT entries
        // number and SCSize as block size
        PSPHT = new PerAddressPredictionHistoryTable(
                KSize, 1 << BHRSize, SCSize);

        // Initialize the saturating counter
        this.SC = new SIPORegister("SC", SCSize, null);
    }

    /**
     * predicts the result of a branch instruction based on the per address BHR and
     * hash value of branch
     * instruction address
     *
     * @param branchInstruction the branch instruction
     * @return the predicted outcome of the branch instruction (taken or not taken)
     */
    @Override
    public BranchResult predict(BranchInstruction branchInstruction) {
        // get BHR value
        ShiftRegister BHR = PABHR.read(branchInstruction.getInstructionAddress());
        // concatenate the instruction address
        // hashing the address
        Bit[] hash = getCacheEntry(branchInstruction.getInstructionAddress(), BHR.read());

        // initialize the PHT if empty
        PSPHT.putIfAbsent(hash, getDefaultBlock());
        // read from the cache
        Bit[] block = PSPHT.get(hash);
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
        ShiftRegister BHR = PABHR.read(instruction.getInstructionAddress());
        Bit[] hash = getCacheEntry(instruction.getInstructionAddress(), BHR.read());

        PSPHT.put(hash, counted);
        // updating the BHR
        BHR.insert(Bit.of(BranchResult.isTaken(actual)));
        PABHR.write(instruction.getInstructionAddress(), BHR.read());
    }

    @Override
    public String monitor() {
        return "PAs predictor snapshot: \n" + PABHR.monitor() + SC.monitor() + PSPHT.monitor();
    }

    private Bit[] getCacheEntry(Bit[] branchAddress, Bit[] BHRValue) {
        // hash the branch address
        Bit[] hashKSize = CombinationalLogic.hash(branchAddress, KSize, hashMode);

        // Concatenate the Hash bits with the BHR bits
        Bit[] cacheEntry = new Bit[hashKSize.length + BHRValue.length];
        System.arraycopy(hashKSize, 0, cacheEntry, 0, hashKSize.length);
        System.arraycopy(BHRValue, 0, cacheEntry, hashKSize.length, BHRValue.length);

        return cacheEntry;
    }

    private Bit[] getDefaultBlock() {
        Bit[] defaultBlock = new Bit[SC.getLength()];
        Arrays.fill(defaultBlock, Bit.ZERO);
        return defaultBlock;
    }
}
