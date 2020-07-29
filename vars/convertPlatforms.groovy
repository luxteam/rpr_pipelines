// "OS-1:GPU-1,...,GPU-N;...;OS-N:GPU-1,...,GPU-N" ===> \n
// ["GPU1-OS1",...,"GPUN-OS1",..., "GPU1-OSN",...,"GPUN-OSN"]

def call(String platforms) {
    def collectedLabels = [];

    // split by os
    String[] byOS;
    byOS = platforms.split(';');
    // split by gpus
    for (String item : byOS) {
        String[] osPlusGPUs;
        osPlusGPUs = item.split(':');
        if (osPlusGPUs.size() > 1)
        {
            os = osPlusGPUs[0]
            String[] gpus;
            gpus = osPlusGPUs[1].split(',')

            for (String gpu: gpus) {
                collectedLabels.add(os + '-' + gpu)
            }
        }
    }
    return collectedLabels;   
}
