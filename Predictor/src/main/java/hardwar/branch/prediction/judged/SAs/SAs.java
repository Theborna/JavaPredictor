package hardwar.branch.prediction.judged.SAs;

import hardwar.branch.prediction.shared.*;
import hardwar.branch.prediction.shared.devices.*;

import java.util.Arrays;

public class SAs implements BranchPredictor {

    private final int branchInstructionSize;
    private final int KSize;
    private final ShiftRegister SC;
    private final RegisterBank PSBHR; // per set branch history register
    private final Cache<Bit[], Bit[]> PSPHT; // per set predication history table
    private final HashMode hashMode;

    public SAs() {
        this(4, 2, 8, 4, HashMode.XOR);
    }

    public SAs(int BHRSize, int SCSize, int branchInstructionSize, int KSize, HashMode hashMode) {
        this.branchInstructionSize = branchInstructionSize;
        this.KSize = KSize;
        this.hashMode = hashMode;

        // Initialize the PSBHR with the given bhr and branch instruction size
        PSBHR = new RegisterBank(KSize, BHRSize);

        // Initializing the PAPHT with K bit as PHT selector and 2^BHRSize row as each
        // PHT entries
        // number and SCSize as block size
        PSPHT = new PerAddressPredictionHistoryTable(
                KSize, 1 << BHRSize, SCSize);

        // Initialize the saturating counter
        this.SC = new SIPORegister("SC", SCSize, null);
    }

    @Override
    public BranchResult predict(BranchInstruction branchInstruction) {
        // get BHR value
        Bit[] address = getAddressLine(branchInstruction.getInstructionAddress());
        ShiftRegister BHR = PSBHR.read(address);
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
    public void update(BranchInstruction branchInstruction, BranchResult actual) {
        // counting from the SC register
        Bit[] counted = CombinationalLogic.count(this.SC.read(),
                BranchResult.isTaken(actual), CountMode.SATURATING);
        // updating our cache
        // get BHR value
        Bit[] address = getAddressLine(branchInstruction.getInstructionAddress());
        ShiftRegister BHR = PSBHR.read(address);
        Bit[] hash = getCacheEntry(branchInstruction.getInstructionAddress(), BHR.read());

        PSPHT.put(hash, counted);
        // updating the BHR
        BHR.insert(Bit.of(BranchResult.isTaken(actual)));
        PSBHR.write(address, BHR.read());
    }

    private Bit[] getAddressLine(Bit[] branchAddress) {
        // hash the branch address
        return CombinationalLogic.hash(branchAddress, KSize, hashMode);
    }

    private Bit[] getCacheEntry(Bit[] branchAddress, Bit[] BHRValue) {
        // Concatenate the branch address bits with the BHR bits
        Bit[] cacheEntry = new Bit[branchAddress.length + BHRValue.length];
        System.arraycopy(branchAddress, 0, cacheEntry, 0, KSize);
        System.arraycopy(BHRValue, 0, cacheEntry, branchAddress.length, BHRValue.length);
        return cacheEntry;
    }

    /**
     * @return a zero series of bits as default value of cache block
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
