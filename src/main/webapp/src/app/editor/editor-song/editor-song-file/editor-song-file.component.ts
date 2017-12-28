import { TranslateService } from '@ngx-translate/core';
import { SongFile } from './../../../models/song-file';
import { MidiRouting } from './../../../models/midi-routing';
import { Component, OnInit } from '@angular/core';
import { BsModalService } from 'ngx-bootstrap/modal';
import { BsModalRef } from 'ngx-bootstrap';
import { Subject } from 'rxjs/Subject';
import { Song } from '../../../models/song';
import { RoutingDetailsComponent } from '../../../routing-details/routing-details.component';
import { SongMidiFile } from '../../../models/song-midi-file';
import { DropzoneConfigInterface } from 'ngx-dropzone-wrapper/dist/lib/dropzone.interfaces';
import { ApiService } from '../../../services/api.service';

@Component({
  selector: 'app-editor-song-file',
  templateUrl: './editor-song-file.component.html',
  styleUrls: ['./editor-song-file.component.scss'],
})
export class EditorSongFileComponent implements OnInit {

  fileIndex: number;
  file: SongFile;
  song: Song;
  onClose: Subject<boolean>;

  dropzoneConfig: DropzoneConfigInterface;
  uploadMessage: string;

  constructor(
    private bsModalRef: BsModalRef,
    private modalService: BsModalService,
    private apiService: ApiService,
    private translateService: TranslateService) {

    this.dropzoneConfig = {
      url: apiService.getRestUrl() + 'file/upload',
      addRemoveLinks: false,
      acceptedFiles: 'audio/*,video/*',
      previewTemplate: `
      <div class="dz-preview dz-file-preview">
        <!-- The attachment details -->
        <div class="dz-details" style="text-align: left">
          <i class="fa fa-file-o"></i> <span data-dz-name></span> <small><span class="label label-default file-size" data-dz-size></span></small>
        </div>
        
        <!--div class="mt-5">
          <span data-dz-errormessage></span>
        </div-->
        
        <div class="progress mt-4 mb-1" style="height: 10px">
          <div class="progress-bar progress-bar-striped progress-bar-animated" role="progressbar" style="width:0%;" data-dz-uploadprogress></div>
        </div>
      </div>
      `
    };

    translateService.get('editor.dropzone-message').map(result => {
      this.uploadMessage = '<h3 class="mb-0"><i class="fa fa-cloud-upload"></i></h3>' + result;
    }).subscribe();
  }

  ngOnInit() {
    this.onClose = new Subject();
  }

  public onOk(): void {
    this.onClose.next(true);
    this.bsModalRef.hide();
  }

  public onCancel(): void {
    this.onClose.next(false);
    this.bsModalRef.hide();
  }

  // Edit the routing details
  editRouting(midiRoutingIndex: number, addNew: boolean = false) {
    // Create a backup of the current song
    let songCopy: Song = new Song(JSON.parse(this.song.stringify()));

    if (addNew) {
      // Add a new routing, if necessary
      let newRouting: MidiRouting = new MidiRouting();
      newRouting.midiDestination = 'OUT_DEVICE';
      (<SongMidiFile>songCopy.fileList[this.fileIndex]).midiRoutingList.push(newRouting);
      midiRoutingIndex = (<SongMidiFile>songCopy.fileList[this.fileIndex]).midiRoutingList.length - 1;
    }

    // Show the routing details dialog
    let routingDialog = this.modalService.show(RoutingDetailsComponent, { keyboard: true, animated: true, backdrop: false, ignoreBackdropClick: true, class: "" });
    (<RoutingDetailsComponent>routingDialog.content).midiRouting = (<SongMidiFile>songCopy.fileList[this.fileIndex]).midiRoutingList[midiRoutingIndex];

    (<RoutingDetailsComponent>routingDialog.content).onClose.subscribe(result => {
      if (result === true) {
        // OK has been pressed -> save
        (<SongMidiFile>this.song.fileList[this.fileIndex]).midiRoutingList[midiRoutingIndex] = (<SongMidiFile>songCopy.fileList[this.fileIndex]).midiRoutingList[midiRoutingIndex];
      }
    });
  }

  // Prevent the last item in the file-list to be draggable.
  // Taken from http://jsbin.com/tuyafe/1/edit?html,js,output
  sortMove(evt) {
    return evt.related.className.indexOf('no-sortjs') === -1;
  }

  public onUploadError(args: any) {
    console.log('Upload error', args);
  }

  public onUploadSuccess(args: any) {
    // Hide the preview element
    args[0].previewElement.hidden = true;

    // Select this file
    this.file = new SongFile(args[1]);
  }

}
