import { Session } from './../models/session';
import { SessionService } from './../services/session.service';
import { Composition } from './../models/composition';
import { CompositionService } from './../services/composition.service';
import { StateService } from './../services/state.service';
import { Set } from './../models/set';
import { Component, OnInit } from '@angular/core';
import { State } from '../models/state';
import { TransportService } from '../services/transport.service';
import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

@Component({
  selector: 'app-play',
  templateUrl: './play.component.html',
  styleUrls: ['./play.component.scss']
})
export class PlayComponent implements OnInit {

  currentSet: Set;
  currentState: State = new State();

  session: Session;

  sets: Set[];

  positionMillis: number = 0;
  playTime: string = '00:00.000';
  playUpdateSubscription: Subscription;
  lastPlayTime: Date;

  // Is the user currently using the slider?
  sliding: boolean = false;

  manualCompositionSelection: boolean = false;

  totalPlayTime: string = '';

  loadingSet: boolean = false;

  constructor(
    public stateService: StateService,
    private compositionService: CompositionService,
    private transportService: TransportService,
    private sessionService: SessionService) {
  }

  ngOnInit() {
    // Subscribe to the state-changed service
    this.stateService.state.subscribe((state: State) => {
      this.stateChanged(state);
    });

    // Subscribe to the get connection service
    this.stateService.getsConnected.subscribe(() => {
      this.loadAllSets();
      this.loadCurrentSet();
    });

    // Load the current state
    this.stateService.getState().subscribe((state: State) => {
      this.stateChanged(state);
      this.currentState = state;
    });

    // Load the current session
    this.sessionService.getSession().subscribe(session => {
      this.session = session;
    });

    this.loadAllSets();
    this.loadCurrentSet();
  }

  private loadAllSets() {
    this.compositionService.getSets().map(result => {
      this.sets = result;
    }).subscribe();
  }

  private updateTotalDuration() {
    let totalDurationMillis: number = 0;

    for (let composition of this.currentSet.compositionList) {
      totalDurationMillis += composition.durationMillis;
    }

    this.totalPlayTime = this.msToTime(totalDurationMillis, false);
  }

  private loadCurrentSet() {
    // Load the current set
    this.loadingSet = true;

    this.compositionService.getCurrentSet(true).finally(() => {
      this.loadingSet = false;
    }).subscribe((set: Set) => {
      this.currentSet = undefined;

      if (set) {
        this.currentSet = set;
        this.updateTotalDuration();
      }

      if (this.currentSet && !this.currentSet.name) {
        // The default set with all compositions is loaded -> display all compositions
        this.compositionService.getCompositions(true).subscribe((compositions: Composition[]) => {
          this.currentSet.compositionList = compositions;
          this.updateTotalDuration();
          this.loadingSet = false;
        });
      } else {
        this.loadingSet = false;
      }
    });
  }

  selectSet(set: Set) {
    let setName: string = '';

    if (set) {
      setName = set.name;
    }

    this.compositionService.loadSet(setName).subscribe();
  }

  private pad(num: number, size: number): string {
    if (!num) {
      num = 0;
    }

    let padded: string = num.toString();
    while (padded.length < size) {
      padded = '0' + padded;
    }

    return padded;
  }

  private msToTime(millis: number, includeMillis: boolean = true): string {
    let ms: number = Math.round(millis % 1000);
    let seconds: number = Math.floor(((millis % 360000) % 60000) / 1000);
    let minutes: number = Math.floor((millis % 3600000) / 60000);

    if (includeMillis) {
      return this.pad(minutes, 2) + ':' + this.pad(seconds, 2) + '.' + this.pad(ms, 3);
    } else {
      return this.pad(minutes, 2) + ':' + this.pad(seconds, 2);
    }
  }

  private stateChanged(newState: State) {
    this.positionMillis = newState.positionMillis;
    this.playTime = this.msToTime(this.positionMillis);

    if (newState.playState == 'PLAYING' && this.currentState.playState != 'PLAYING') {
      if (this.playUpdateSubscription) {
        this.playUpdateSubscription.unsubscribe;
      }

      // Save the last time, we started the composition. Don't use device time, as it may be wrong.
      this.lastPlayTime = new Date();

      let playUpdater = Observable.timer(0, 10);
      this.playUpdateSubscription = playUpdater.subscribe(() => {
        let currentTime = new Date();
        let positionMillis = currentTime.getTime() - this.lastPlayTime.getTime() + this.currentState.positionMillis;

        if (!this.sliding && this.currentState.playState != 'STOPPING') {
          if (positionMillis > 0) {
            this.playTime = this.msToTime(positionMillis);
          }

          this.positionMillis = positionMillis;
        }
      });
    }

    if (newState.playState == 'STOPPING' || newState.playState == 'STOPPING' || newState.playState == 'PAUSED') {
      if (this.playUpdateSubscription) {
        this.playUpdateSubscription.unsubscribe();
      }
    }

    // Scroll the corresponding composition into the view, except the user selected the
    // composition here in the app.
    if (this.manualCompositionSelection) {
      // The next time, we receive a new composition state, we should scroll into the view again
      this.manualCompositionSelection = false;
    } else {
      let compositionObject = document.querySelector('#composition' + newState.currentCompositionIndex);
      if (compositionObject) {
        compositionObject.scrollIntoView();
      }

      let compositionSmallObject = document.querySelector('#compositionSmall' + newState.currentCompositionIndex);
      if (compositionSmallObject) {
        compositionSmallObject.scrollIntoView();
      }
    }

    // The current set changed
    if (newState.currentSetName != this.currentState.currentSetName) {
      this.loadCurrentSet();
    }

    this.currentState = newState;
  }

  play() {
    this.currentState.playState = 'LOADING';
    this.transportService.play().subscribe();
  }

  stop() {
    this.currentState.playState = 'STOPPING';
    this.transportService.stop().subscribe();
  }

  pause() {
    this.transportService.pause().subscribe();
  }

  slideStart() {
    this.sliding = true;
  }

  slideStop(positionMillis: number) {
    this.currentState.playState = 'STOPPING';

    // Only seeking to seconds is possible at the moment
    this.positionMillis = positionMillis - (positionMillis % 1000);

    this.transportService.seek(this.positionMillis).subscribe();

    this.sliding = false;
  }

  slideChange(event: any) {
    this.positionMillis = event.newValue;
    this.playTime = this.msToTime(this.positionMillis);
  }

  nextComposition() {
    this.transportService.nextComposition().subscribe();
  }

  previousComposition() {
    this.transportService.previousComposition().subscribe();
  }

  setComposition(index: number, composition: Composition) {
    this.manualCompositionSelection = true;

    if (this.currentSet && !this.currentSet.name) {
      // We got the default set loaded -> select compositions by name
      this.transportService.setCompositionName(composition.name).subscribe();
    } else {
      // We got a real set loaded -> select compositions by index
      this.transportService.setCompositionIndex(index).subscribe();
    }
  }

  toggleAutoSelectNextSong() {
    this.session.autoSelectNextComposition = !this.session.autoSelectNextComposition;

    this.sessionService.setAutoSelectNextComposition(this.session.autoSelectNextComposition).subscribe();
  }

}
