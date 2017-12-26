import { SongVideoFile } from './song-video-file';
import { SongMidiFile } from "./song-midi-file";
import { SongAudioFile } from "./song-audio-file";
import { SongFile } from "./song-file";

export class Song {
    name: string;
    durationMillis: number;
    isNew: boolean = false;
    fileList: SongFile[] = [];

    constructor(data?: any) {
        if (!data) {
            return;
        }

        this.name = data.name;
        this.durationMillis = data.durationMillis;

        this.fileList = this.parseFileList(data);
    }

    private parseFileList(data: any): SongFile[] {
        let fileList: SongFile[] = [];

        if (data.fileList) {
            for (let file of data.fileList) {
                if (file.midiFile) {
                    let midiFile = new SongMidiFile(file.midiFile);
                    midiFile.type = "midi";
                    fileList.push(midiFile);
                } else if (file.audioFile) {
                    let audioFile = new SongAudioFile(file.audioFile);
                    audioFile.type = "audio";
                    fileList.push(audioFile);
                } else if (file.videoFile) {
                    let videoFile = new SongVideoFile(file.audioFile);
                    videoFile.type = "video";
                    fileList.push(videoFile);
                }
            }
        }

        return fileList;
    }

    // Return a file object based on its type
    public static getFileObjectByType(data: any) {
        if (data.type == 'midi') {
            let midiFile = new SongMidiFile(data);
            midiFile.type = "midi";
            return midiFile;
        } else if (data.type == 'audio') {
            let audioFile = new SongAudioFile(data);
            audioFile.type = "audio";
            return audioFile;
        } else if (data.type == 'video') {
            let videoFile = new SongVideoFile(data);
            videoFile.type = "video";
            return videoFile;
        }
    }

    // Stringify the song and it's files correct (JSON would ignore the extended file classes by default)
    stringify(): string {
        let songString = JSON.stringify(this);
        let songObject = JSON.parse(songString);

        songObject.fileList = [];

        for (let file of this.fileList) {
            if (file instanceof SongMidiFile) {
                let fileObj: any = {};
                fileObj.midiFile = file;
                songObject.fileList.push(fileObj);
            } else if (file instanceof SongAudioFile) {
                let fileObj: any = {};
                fileObj.audioFile = file;
                songObject.fileList.push(fileObj);
            } else if (file instanceof SongVideoFile) {
                let fileObj: any = {};
                fileObj.videoFile = file;
                songObject.fileList.push(fileObj);
            }
        }

        songObject.isNew = undefined;

        return JSON.stringify(songObject);
    }
}
