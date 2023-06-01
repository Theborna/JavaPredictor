package hardwar.branch.prediction.judged.PAg;

import hardwar.branch.prediction.shared.*;
import hardwar.branch.prediction.shared.devices.*;

import java.util.Arrays;

public class PAg implements BranchPredictor {
    private final ShiftRegister SC; // saturating counter register
    private final RegisterBank PABHR; // per address branch history register
    private final Cache<Bit[], Bit[]> PHT; // page history table

    public PAg() {
        this(4, 2, 8);
    }

    /**
     * Creates a new PAg predictor with the given BHR register size and initializes the PABHR based on
     * the branch instruction size and BHR size
     *
     * @param BHRSize               the size of the BHR register
     * @param SCSize                the size of the register which hold the saturating counter value
     * @param branchInstructionSize the number of bits which is used for saving a branch instruction
     */
    public PAg(int BHRSize, int SCSize, int branchInstructionSize) {
        // Initialize the BHR register with the given size and no default value
        this.PABHR = new RegisterBank(branchInstructionSize, BHRSize);

        // Initialize the PHT with a size of 2^size and each entry having a saturating
        // counter of size "SCSize"
        this.PHT = new PageHistoryTable(1 << BHRSize, SCSize);

        // Initialize the SC register
        this.SC = new SIPORegister("SC", SCSize, null);
    }

    /**
     * @param instruction the branch instruction
     * @return the predicted outcome of the branch instruction (taken or not taken)
     */
    @Override
    public BranchResult predict(BranchInstruction instruction) {
        ShiftRegister BHR = PABHR.read(instruction.getInstructionAddress());
        // read the bhr
        Bit[] bhrData = BHR.read();
        // initialize the PHT if empty
        PHT.putIfAbsent(bhrData, getDefaultBlock());
        // read from the cache
        Bit[] block = PHT.get(bhrData);
        // load into the SC register
        SC.load(block);
        PABHR.write(instruction.getInstructionAddress(), BHR.read());
        return BranchResult.of(block[0].getValue());
    }

    /**
     * @param instruction the branch instruction
     * @param actual      the actual result of branch (taken or not)
     */
    @Override
    public void update(BranchInstruction instruction, BranchResult actual) {
        ShiftRegister BHR = PABHR.read(instruction.getInstructionAddress());
        // counting from the SC register
        Bit[] counted = CombinationalLogic.count(this.SC.read(),
                BranchResult.isTaken(actual), CountMode.SATURATING);
        // updating our cache
        PHT.put(BHR.read(), counted);
        // updating the BHR
        BHR.insert(Bit.of(BranchResult.isTaken(actual)));
        PABHR.write(instruction.getInstructionAddress(), BHR.read());
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
        return "PAg predictor snapshot: \n" + PABHR.monitor() + SC.monitor() + PHT.monitor();
    }
}
