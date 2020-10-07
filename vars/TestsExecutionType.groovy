public class TestsExecutionType {
    public static enum TestsExecutionTypeValues {

        //take only one node for test groups on each GPU
        TAKE_ONE_NODE_PER_GPU,
        //number of parallel threads is equal to number of existent nodes in Jenkins before run tests
        TAKE_ALL_NODES

    }

    def static valueOf(String name) {
        switch(name) {
            case 'TakeOneNodePerGPU':
                return TestsExecutionTypeValues.TAKE_ONE_NODE_PER_GPU
            case 'TakeAllNodes':
                return TestsExecutionTypeValues.TAKE_ALL_NODES
            default:
                return null
        }
    }

    def static TAKE_ONE_NODE_PER_GPU = TestsExecutionTypeValues.TAKE_ONE_NODE_PER_GPU
    def static TAKE_ALL_NODES = TestsExecutionTypeValues.TAKE_ALL_NODES
}
