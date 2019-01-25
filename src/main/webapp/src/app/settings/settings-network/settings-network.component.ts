import { TranslateService } from '@ngx-translate/core';
import { RemoteDevice } from './../../models/remote-device';
import { Settings } from './../../models/settings';
import { SettingsService } from './../../services/settings.service';
import { Component, OnInit, OnDestroy } from '@angular/core';
import { map } from "rxjs/operators";
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-settings-network',
  templateUrl: './settings-network.component.html',
  styleUrls: ['./settings-network.component.scss']
})
export class SettingsNetworkComponent implements OnInit, OnDestroy {

  private settingsChangedSubscription: Subscription;

  settings: Settings;

  constructor(
    private settingsService: SettingsService,
    private translateService: TranslateService
  ) { }

  private loadSettings() {
    this.settingsService.getSettings().pipe(map(result => {
      this.settings = result;
    })).subscribe();
  }

  ngOnInit() {
    this.loadSettings();

    this.settingsChangedSubscription = this.settingsService.settingsChanged.subscribe(() => {
      this.loadSettings();
    });
  }

  ngOnDestroy() {
    this.settingsChangedSubscription.unsubscribe();
  }

  addRemoteDevice() {
    this.translateService.get('settings.remote-device-name-placeholder').subscribe(result => {
      let remoteDevice: RemoteDevice = new RemoteDevice();
      remoteDevice.name = result + ' ' + (this.settings.remoteDeviceList.length + 1);
      this.settings.remoteDeviceList.push(remoteDevice);
    });
  }

  deleteRemoteDevice(remoteDeviceIndex: number) {
    this.settings.remoteDeviceList.splice(remoteDeviceIndex, 1);
  }

  // Prevent the last item in the file-list to be draggable.
  // Taken from http://jsbin.com/tuyafe/1/edit?html,js,output
  sortMove(evt) {
    return evt.related.className.indexOf('no-sortjs') === -1;
  }

}
