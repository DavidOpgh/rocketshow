export class MidiControl {
    action: string = 'PLAY';
    selectComposition: string;
    channelFrom: number = 0;
    noteFrom: number = 0;
    remoteDeviceList: string[] = [];
    executeLocally: boolean = true;

    constructor(data?: any) {
        if(!data) {
        	return;
        }
        
        this.action = data.action;
        this.selectComposition = data.selectComposition;
        this.channelFrom = data.channelFrom;
        this.noteFrom = data.noteFrom;

        if(data.remoteDeviceList) {
            for(let remoteDevice of data.remoteDeviceList) {
                this.remoteDeviceList.push(remoteDevice);
            }
        }

        this.executeLocally = data.executeLocally;
    }
}
