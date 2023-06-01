package hardwar.branch.prediction.judged.PAp;


import hardwar.branch.prediction.shared.*;
import hardwar.branch.prediction.shared.devices.*;

import java.util.Arrays;

public class PAp implements BranchPredictor {

    private final int branchInstructionSize;

    private final ShiftRegister SC; // saturating counter register

    private final RegisterBank PABHR; // per address branch history register

    private final Cache<Bit[], Bit[]> PAPHT; // Per Address Predication History Table

    public PAp() {
        this(4, 2, 8);
    }

    public PAp(int BHRSize, int SCSize, int branchInstructionSize) {
        // TODO: complete the constructor
        this.branchInstructionSize = branchInstructionSize;


        // Initialize the PABHR with the given bhr and branch instruction size
        PABHR = new RegisterBank(branchInstructionSize, BHRSize);

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
        ShiftRegister BHR = PABHR.read(branchInstruction.getInstructionAddress());
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
        ShiftRegister BHR = PABHR.read(instruction.getInstructionAddress());
        Bit[] hash = getCacheEntry(instruction.getInstructionAddress(), BHR.read());

        PAPHT.put(hash, counted);
        // updating the BHR
        BHR.insert(Bit.of(BranchResult.isTaken(actual)));
        PABHR.write(instruction.getInstructionAddress(), BHR.read());
    }


    private Bit[] getCacheEntry(Bit[] branchAddress, Bit[] BHRValue) {
        // Concatenate the branch address bits with the BHR bits
        Bit[] cacheEntry = new Bit[branchAddress.length + BHRValue.length];
        System.arraycopy(branchAddress, 0, cacheEntry, 0, branchInstructionSize);
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
        return "PAp predictor snapshot: \n" + PABHR.monitor() + SC.monitor() + PAPHT.monitor();
    }
}
